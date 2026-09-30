import { api, type Account, type AccountsSnapshot } from "./ipc";

/** Reactive mirror of the account store. The Rust side is the source of truth:
 *  every mutation returns a fresh snapshot, which replaces this state wholesale. */
class Accounts {
  list = $state<Account[]>([]);
  activeId = $state<string | null>(null);
  active = $derived(this.list.find((a) => a.id === this.activeId) ?? null);

  apply(snapshot: AccountsSnapshot) {
    this.list = snapshot.accounts;
    this.activeId = snapshot.activeId;
  }

  async load() {
    this.apply(await api.listAccounts());
  }

  async switchTo(id: string) {
    this.apply(await api.switchAccount(id));
  }

  async remove(id: string) {
    this.apply(await api.removeAccount(id));
  }
}

export const accounts = new Accounts();
