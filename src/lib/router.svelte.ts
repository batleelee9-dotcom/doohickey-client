export type Route =
  | { name: "home" }
  | { name: "servers" }
  | { name: "skins" }
  | { name: "settings"; section?: string };

/** A tiny in-memory router: the launcher has no URLs worth deep-linking. */
class Router {
  route = $state<Route>({ name: "home" });
  private history: Route[] = [];

  go(route: Route) {
    this.history.push(this.route);
    if (this.history.length > 30) this.history.shift();
    this.route = route;
  }

  back() {
    this.route = this.history.pop() ?? { name: "home" };
  }

  is(name: Route["name"]) {
    return this.route.name === name;
  }
}

export const router = new Router();
