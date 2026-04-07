let _consoleLines = [];
let _consoleEventCleanup = null;
let _consoleGameRunning = false;

function ConsolePageInit() {
  const page = document.getElementById('page-console');

  const isRunning = MinecraftLauncher.getState() === 'running' || MinecraftLauncher.getState() === 'starting';
  _consoleGameRunning = isRunning;

  if (!isRunning && _consoleLines.length === 0) {
    // Show placeholder with icon
    page.innerHTML = `
      <div class="console-container">
        <div class="console-header">
          <div class="console-title">Console</div>
        </div>
        <div class="console-placeholder">
          <img class="console-placeholder-icon" src="assets/icon.png" alt="Icey Client" onerror="this.style.display='none'">
          <div class="console-placeholder-text">Launch a game to see console output</div>
        </div>
      </div>
    `;
  } else {
    _renderConsoleWithOutput(page);
  }

  // Listen for MC events
  if (_consoleEventCleanup) _consoleEventCleanup();
  _consoleEventCleanup = window.icey.onMcEvent((data) => {
    if (data.type === 'console-log') {
      _consoleLines.push({ text: data.message, level: data.level || 'info' });
      _appendConsoleLine(data.message, data.level || 'info');
    }
    if (data.type === 'mc-started') {
      _consoleGameRunning = true;
      // Remove placeholder if showing
      const placeholder = document.querySelector('.console-placeholder');
      if (placeholder) {
        const page = document.getElementById('page-console');
        _renderConsoleWithOutput(page);
      }
    }
    if (data.type === 'mc-stopped') {
      _consoleGameRunning = false;
      _consoleLines.push({ text: `--- Game exited (code: ${data.code ?? 'unknown'}) ---`, level: 'info' });
      _appendConsoleLine(`--- Game exited (code: ${data.code ?? 'unknown'}) ---`, 'info');
    }
  });
}

function _renderConsoleWithOutput(page) {
  page.innerHTML = `
    <div class="console-container">
      <div class="console-header">
        <div class="console-title">Console</div>
        <div class="console-actions">
          <button class="console-btn" onclick="_clearConsole()">Clear</button>
        </div>
      </div>
      <div class="console-output" id="console-output">
        ${_consoleLines.map(l => `<div class="console-line console-line-${l.level}">${_escapeConsole(l.text)}</div>`).join('')}
      </div>
    </div>
  `;

  // Auto-scroll to bottom
  const output = document.getElementById('console-output');
  if (output) output.scrollTop = output.scrollHeight;
}

function _appendConsoleLine(text, level) {
  const output = document.getElementById('console-output');
  if (!output) return;
  const line = document.createElement('div');
  line.className = `console-line console-line-${level}`;
  line.textContent = text;
  output.appendChild(line);
  output.scrollTop = output.scrollHeight;
}

function _clearConsole() {
  _consoleLines = [];
  const output = document.getElementById('console-output');
  if (output) output.innerHTML = '';
}

function _escapeConsole(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
