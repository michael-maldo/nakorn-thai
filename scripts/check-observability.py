#!/usr/bin/env python3
"""Check the packaged backend using a disposable DB and a local OTLP receiver.
Run after mvn verify with DB_TEST_URL / DB_TEST_USERNAME / DB_TEST_PASSWORD set.
Requires Python 3 and Java 21+. Does not contact Loki or Tempo.
"""
import http.server
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


payloads = []
class Receiver(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        payloads.append((self.path, self.rfile.read(int(self.headers['Content-Length']))))
        self.send_response(200)
        self.send_header('Content-Type', 'application/x-protobuf')
        self.end_headers()

    def log_message(self, *args):
        pass


def get(port, path, headers=None):
    request = urllib.request.Request(f'http://127.0.0.1:{port}{path}', headers=headers or {})
    try:
        with urllib.request.urlopen(request, timeout=3) as response:
            return response.status, response.headers, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read().decode()


if __name__ == '__main__':
    db = os.environ['DB_TEST_URL']  # Intentionally no fallback to an application/production DB.
    root = Path(__file__).resolve().parents[1]
    jar = root / 'backend/target/nakorn-thai-backend-0.1.0-SNAPSHOT.jar'
    api_port, management_port = free_port(), free_port()
    receiver = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Receiver)
    threading.Thread(target=receiver.serve_forever, daemon=True).start()
    trace_id = '1234567890abcdef1234567890abcdef'
    with tempfile.TemporaryDirectory(prefix='nakorn-observability-') as directory:
        log_file = Path(directory) / 'backend.json.log'
        env = dict(os.environ, DB_URL=db,
                   DB_USERNAME=os.environ.get('DB_TEST_USERNAME', 'nakorn_test'),
                   DB_PASSWORD=os.environ.get('DB_TEST_PASSWORD', ''),
                   SERVER_PORT=str(api_port), MANAGEMENT_PORT=str(management_port),
                   SERVER_ADDRESS='127.0.0.1', MANAGEMENT_ADDRESS='127.0.0.1',
                   SPRING_PROFILES_ACTIVE='dev', APP_ENV='test', LOG_FILE=str(log_file),
                   TRACING_EXPORT_ENABLED='true', TRACING_SAMPLING_PROBABILITY='1.0',
                   OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=f'http://127.0.0.1:{receiver.server_port}/v1/traces')
        java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java'
        with (Path(directory) / 'console.log').open('w+') as output:
            process = subprocess.Popen([java, '-jar', str(jar)], env=env, stdout=output, stderr=output)
            try:
                deadline = time.monotonic() + 45
                while True:
                    try:
                        if get(management_port, '/actuator/health')[0] == 200:
                            break
                    except (OSError, urllib.error.URLError):
                        pass
                    if process.poll() is not None or time.monotonic() > deadline:
                        raise AssertionError('Backend did not become healthy')
                    time.sleep(0.2)
                path = '/api/menu/collections/signature-dishes/items'
                status, headers, body = get(api_port, path + '?secret=DO_NOT_LOG_THIS',
                                           {'traceparent': f'00-{trace_id}-1234567890abcdef-01'})
                assert status == 200 and len(json.loads(body)['items']) == 4
                assert headers.get('X-Request-ID')
                assert get(api_port, '/api/menu/collections/missing/items')[0] == 404
                assert get(api_port, '/actuator/prometheus')[0] == 404
                status, _, metrics = get(management_port, '/actuator/prometheus')
                assert status == 200
                assert 'http_server_requests_seconds_count' in metrics
                assert 'http_server_requests_seconds_bucket' in metrics
                assert 'uri="/api/menu/collections/{slug}/items"' in metrics
                assert 'status="404"' in metrics and 'jvm_memory_used_bytes' in metrics
                deadline = time.monotonic() + 20
                while not any(bytes.fromhex(trace_id) in body for _, body in payloads):
                    if time.monotonic() > deadline:
                        raise AssertionError('Incoming trace was not exported over OTLP')
                    time.sleep(0.2)
                records = [json.loads(line) for line in log_file.read_text().splitlines()]
                requests = [r for r in records if r.get('event') == 'http_request_completed']
                request = next(r for r in requests if r.get('trace_id') == trace_id)
                assert request['request_id'] == headers['X-Request-ID']
                assert request['http_route'] == '/api/menu/collections/{slug}/items'
                assert request['http_status'] == 200 and request['duration_ms'] >= 0
                assert request['span_id'] and request['service_name'] == 'nakorn-thai-backend'
                assert request['environment'] == 'test'
                assert 'DO_NOT_LOG_THIS' not in log_file.read_text()
                assert any(path == '/v1/traces' for path, _ in payloads)
                print('Passed: HTTP metrics/histograms, private management port, JSON request logs, correlation, W3C propagation and OTLP trace export.')
            except Exception:
                output.flush()
                output.seek(0)
                print(output.read()[-12000:])
                raise
            finally:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                receiver.shutdown()
                receiver.server_close()
