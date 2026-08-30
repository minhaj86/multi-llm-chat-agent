// Applies the saved theme before React renders to avoid a flash of the wrong theme.
;(function () {
  try {
    var stored = JSON.parse(localStorage.getItem('ai-workspace-preferences') || '{}')
    var theme = (stored.state && stored.state.theme) || 'system'
    var dark = theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
    document.documentElement.classList.toggle('dark', dark)
  } catch (e) {
    /* ignore */
  }
})()
