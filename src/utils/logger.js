const Logger = {
  info(msg) {
    // In renderer, logging is handled via IPC - main process writes to file
    // This is a no-op in renderer to avoid console.log
  },
  warn(msg) {},
  error(msg) {}
};
