const Toast = {
  _container: null,

  _getContainer() {
    if (!this._container) {
      this._container = document.getElementById('toast-container');
    }
    return this._container;
  },

  show(message, level = 'info') {
    const container = this._getContainer();
    const toast = document.createElement('div');
    toast.className = 'toast ' + level;

    // Like the game's toasts: the head where an item would be, a headline
    // in the game's yellow (green when something worked, red when it did
    // not), the message in white. A click sends it away.
    toast.innerHTML = `
      <img class="toast-icon" src="assets/icon.png" alt="">
      <div class="toast-text">
        <div class="toast-head" data-no-i18n>Icey Client</div>
        <div class="toast-message">${message}</div>
      </div>
    `;

    let gone = false;
    const dismiss = () => {
      if (gone) return;
      gone = true;
      toast.classList.add('dismissing');
      setTimeout(() => toast.remove(), 260);
    };

    toast.addEventListener('click', dismiss);

    container.prepend(toast);

    let timer = setTimeout(dismiss, 3500);
    toast.addEventListener('mouseenter', () => clearTimeout(timer));
    toast.addEventListener('mouseleave', () => { timer = setTimeout(dismiss, 1500); });
  },

  info(message) { this.show(message, 'info'); },
  success(message) { this.show(message, 'success'); },
  error(message) { this.show(message, 'error'); }
};
