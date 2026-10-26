import {useEffect,useState} from 'react';
import {allowMenuNavigation,hasMenuEdits,cancelPendingMenuNavigation,createMenuNavigationApproval} from '../features/menu/model/menuAdminNavigation';
export default function useProtoRoute(){
 const [location,setLocation]=useState(window.location.hash);
  useEffect(() => {
    let currentUrl = window.location.href;
    let approvedUrl = null;
    let position = window.history.state?.protoMenuPosition ?? 0;
    let restoring = false;
    let pendingTransition = null;
    let mounted = true;
    let observedUrl = currentUrl, observedPosition = position;
    const resume = (targetUrl, targetPosition, ticket) => {
      ticket ??= createMenuNavigationApproval();
      pendingTransition = { targetUrl, targetPosition, ticket };
      if (!restoring) finishTransition();
      return true;
    };
    const finishTransition = () => {
      const transition = pendingTransition;
      pendingTransition = null;
      if (!mounted || !transition?.ticket.complete()) return;
      approvedUrl = transition.targetUrl;
      if (Number.isInteger(transition.targetPosition) && transition.targetPosition !== position) {
        window.history.go(transition.targetPosition - position);
      } else window.location.assign(transition.targetUrl);
    };
    window.history.replaceState({ ...window.history.state, protoMenuPosition: position }, '');
    const beforeLink = event => {
      const link = event.target.closest?.('a[href]');
      if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || link.target === '_blank') return;
      if (link.href === window.location.href) return;
      pendingTransition?.ticket.cancel(); pendingTransition = null;
      approvedUrl = null;
      if (!allowMenuNavigation(undefined, undefined, ticket => resume(link.href, undefined, ticket))) event.preventDefault();
      else if (restoring) { event.preventDefault(); resume(link.href); }
      else approvedUrl = link.href;
    };
    const navigate = () => {
      const targetPosition = window.history.state?.protoMenuPosition;
      const targetUrl = window.location.href;
      // A traversal emits popstate and often hashchange. Process each physical entry once.
      if (targetUrl === observedUrl && targetPosition === observedPosition) return;
      observedUrl = targetUrl; observedPosition = targetPosition;
      if (restoring && targetUrl === currentUrl && targetPosition === position) {
        restoring = false; finishTransition(); return;
      }
      pendingTransition?.ticket.cancel(); pendingTransition = null;
      const allowed = approvedUrl === targetUrl || allowMenuNavigation(undefined, undefined, ticket => resume(targetUrl, targetPosition, ticket));
      if (!allowed || restoring) {
        if (allowed) resume(targetUrl, targetPosition);
        // A new native traversal can interrupt restoration. Re-target the latest
        // observed entry to the editor, without ever resuming approval before arrival.
        // Undo Back/Forward without replacing the entry the user may revisit.
        if (Number.isInteger(targetPosition) && targetPosition !== position) {
          restoring = true; window.history.go(position - targetPosition);
        } else if (!restoring) window.history.replaceState({ ...window.history.state, protoMenuPosition: position }, '', currentUrl);
        return;
      }
      position = Number.isInteger(targetPosition) ? targetPosition : position + 1;
      window.history.replaceState({ ...window.history.state, protoMenuPosition: position }, '');
      approvedUrl = null; currentUrl = window.location.href;
      observedUrl = currentUrl; observedPosition = position;
      setLocation(window.location.hash);
    };
    const beforeUnload = event => { if (hasMenuEdits()) { event.preventDefault(); event.returnValue = ''; } };
    document.addEventListener('click', beforeLink);
    window.addEventListener('beforeunload', beforeUnload);
    window.addEventListener('hashchange', navigate);
    window.addEventListener('popstate', navigate);
    return () => { mounted = false; pendingTransition?.ticket.cancel(); pendingTransition = null; cancelPendingMenuNavigation(); window.removeEventListener('hashchange', navigate); window.removeEventListener('popstate', navigate); document.removeEventListener('click', beforeLink); window.removeEventListener('beforeunload', beforeUnload); };
  }, []);
 return location;
}
