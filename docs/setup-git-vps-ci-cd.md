I’ll check your project and deployment notes, then outline a GitHub Actions workflow that deploys to your VPS.

Use **GitHub Actions** to build your site and upload it to your VPS whenever you push to `main`:

```text
git push → GitHub Actions → build frontend → upload over SSH → VPS serves site
```

Your frontend has a Vite build command, but your backend Maven file, Dockerfile, and production Compose file are empty. The setup below deploys **the frontend website**. Backend deployment can be added once it runs.

These steps assume an **Ubuntu/Debian VPS**, Nginx, and SSH on port `22`.

1. **Prepare your VPS**

   Log into your VPS using your existing administrator account, then run:

   ```bash
   sudo apt update
   sudo apt install -y nginx rsync
   sudo adduser --disabled-password --gecos "" deploy

   sudo install -d -o deploy -g deploy /var/www/nakorn-thai
   sudo install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
   ```

   The `deploy` account will upload website files without needing sudo.

2. **Create a deployment SSH key on your computer**

   ```bash
   ssh-keygen -t ed25519 -C "github-actions-nakorn-thai" \
     -f ~/.ssh/nakorn-thai-deploy
   ```

   Leave the passphrase empty for unattended deployment.

   This creates:

   - `nakorn-thai-deploy`: private key, stored in GitHub Actions secrets.
   - `nakorn-thai-deploy.pub`: public key, installed on the VPS.

   Display the public key:

   ```bash
   cat ~/.ssh/nakorn-thai-deploy.pub
   ```

   On the VPS, paste that public key as a single line into:

   ```bash
   sudo nano /home/deploy/.ssh/authorized_keys
   ```

   Then set permissions:

   ```bash
   sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
   sudo chmod 600 /home/deploy/.ssh/authorized_keys
   ```

3. **Verify the VPS identity and SSH connection**

   On the VPS, display its host key fingerprint:

   ```bash
   sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
   ```

   On your computer, collect its public host key:

   ```bash
   ssh-keyscan -t ed25519 YOUR_VPS_IP > /tmp/nakorn-vps-known-hosts
   ssh-keygen -lf /tmp/nakorn-vps-known-hosts
   ```

   Confirm the fingerprints match, then test:

   ```bash
   ssh -i ~/.ssh/nakorn-thai-deploy \
     -o UserKnownHostsFile=/tmp/nakorn-vps-known-hosts \
     -o StrictHostKeyChecking=yes \
     deploy@YOUR_VPS_IP
   ```

4. **Configure Nginx**

   On the VPS:

   ```bash
   sudo nano /etc/nginx/sites-available/nakorn-thai
   ```

   Add this, replacing the domain:

   ```nginx
   server {
       listen 80;
       server_name example.com www.example.com;

       root /var/www/nakorn-thai/current;
       index index.html;

       location / {
           try_files $uri $uri/ /index.html;
       }
   }
   ```

   Enable it:

   ```bash
   sudo ln -s /etc/nginx/sites-available/nakorn-thai \
     /etc/nginx/sites-enabled/nakorn-thai
   sudo nginx -t
   sudo systemctl reload nginx
   ```

   Point your domain’s DNS records to the VPS and allow HTTP traffic through your VPS firewall. Configure HTTPS before launching publicly. The site will become available after the first deployment.

5. **Add GitHub Actions secrets**

   In your GitHub repository, open **Settings → Secrets and variables → Actions → New repository secret**.

   | Secret | Value |
   |---|---|
   | `VPS_HOST` | Your VPS IP address |
   | `VPS_SSH_KEY` | Entire contents of `~/.ssh/nakorn-thai-deploy` |
   | `VPS_KNOWN_HOSTS` | Verified contents of `/tmp/nakorn-vps-known-hosts` |

   GitHub makes these values available only when the workflow explicitly references them. [GitHub secrets documentation](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)

6. **Create `.github/workflows/deploy.yml`**

   Ensure `frontend/package-lock.json` is committed; `npm ci` requires it.

   ```yaml
   name: Deploy website

   on:
     push:
       branches: [main]
     workflow_dispatch:

   permissions:
     contents: read

   concurrency:
     group: production-deploy
     cancel-in-progress: false

   jobs:
     deploy:
       if: github.ref == 'refs/heads/main'
       runs-on: ubuntu-latest
       timeout-minutes: 15

       steps:
         - uses: actions/checkout@v4

         - uses: actions/setup-node@v4
           with:
             node-version: '22'
             cache: npm
             cache-dependency-path: frontend/package-lock.json

         - name: Install and build
           working-directory: frontend
           run: |
             npm ci
             npm run build
             test -s dist/index.html

         - name: Configure SSH
           env:
             SSH_KEY: ${{ secrets.VPS_SSH_KEY }}
             KNOWN_HOSTS: ${{ secrets.VPS_KNOWN_HOSTS }}
           run: |
             install -d -m 700 ~/.ssh
             printf '%s\n' "$SSH_KEY" > ~/.ssh/id_ed25519
             printf '%s\n' "$KNOWN_HOSTS" > ~/.ssh/known_hosts
             chmod 600 ~/.ssh/id_ed25519 ~/.ssh/known_hosts

         - name: Upload and activate release
           env:
             VPS_HOST: ${{ secrets.VPS_HOST }}
           run: |
             set -euo pipefail
             release="${GITHUB_SHA}-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
             destination="/var/www/nakorn-thai/releases/$release"

             ssh -o BatchMode=yes -o StrictHostKeyChecking=yes \
               "deploy@$VPS_HOST" "mkdir -p '$destination'"

             rsync -az \
               -e "ssh -o BatchMode=yes -o StrictHostKeyChecking=yes" \
               frontend/dist/ "deploy@$VPS_HOST:$destination/"

             ssh -o BatchMode=yes -o StrictHostKeyChecking=yes \
               "deploy@$VPS_HOST" \
               "ln -s '$destination' '/var/www/nakorn-thai/current-$release' &&
                mv -Tf '/var/www/nakorn-thai/current-$release' \
                  /var/www/nakorn-thai/current"

         - name: Remove deployment key
           if: always()
           run: rm -f ~/.ssh/id_ed25519
   ```

   Each upload gets a separate release directory. Nginx switches to it only after the upload completes. Previous releases remain available for rollback and will need occasional cleanup.

   GitHub recognizes workflows in `.github/workflows/`; the `push` trigger above runs on updates to `main`. [Workflow documentation](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)

7. **Push your changes**

   ```bash
   git add .github/workflows/deploy.yml frontend/package-lock.json
   git commit -m "Add automatic VPS frontend deployment"
   git push origin main
   ```

   Open your repository’s **Actions** tab to watch deployment. Subsequent pushes to `main` repeat the build and upload automatically.

This currently checks that the frontend builds; it does not run application tests or deploy the Java backend. These are setup instructions—the VPS and repository have not been changed.