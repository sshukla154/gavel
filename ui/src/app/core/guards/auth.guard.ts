import { CanActivateFn } from '@angular/router';
import { createAuthGuard, AuthGuardData } from 'keycloak-angular';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

const isAccessAllowed = async (
  _route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
  { authenticated, keycloak }: AuthGuardData
): Promise<boolean> => {
  if (authenticated) {
    return true;
  }
  await keycloak.login({ redirectUri: window.location.origin + state.url });
  return false;
};

export const authGuard = createAuthGuard<CanActivateFn>(isAccessAllowed);
