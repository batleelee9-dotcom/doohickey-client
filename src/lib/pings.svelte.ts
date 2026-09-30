import { api, toAppError, type ServerStatus } from "./ipc";

export type PingState =
  | { state: "loading" }
  | { state: "online"; data: ServerStatus; at: number }
  | { state: "offline"; error: string; at: number };

/** Server status shared by Home and Servers, refreshed at most once a minute. */
class Pings {
  byAddress = $state<Record<string, PingState>>({});

  async ping(address: string, force = false) {
    const current = this.byAddress[address];
    if (!force && current && (current.state === "loading" || Date.now() - current.at < 60_000)) return;
    this.byAddress[address] = { state: "loading" };
    try {
      this.byAddress[address] = { state: "online", data: await api.pingServer(address), at: Date.now() };
    } catch (e) {
      this.byAddress[address] = { state: "offline", error: toAppError(e).message, at: Date.now() };
    }
  }

  /** Pings several servers with limited parallelism. */
  async pingAll(addresses: string[], force = false) {
    const queue = [...new Set(addresses)];
    const worker = async () => {
      for (let a = queue.shift(); a; a = queue.shift()) await this.ping(a, force);
    };
    await Promise.all(Array.from({ length: Math.min(6, queue.length) }, worker));
  }
}

export const pings = new Pings();
