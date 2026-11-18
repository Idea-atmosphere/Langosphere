// The saved theme before the first paint (no dark flash on the light theme). A classic script every page loads in
// its <head>, before any stylesheet applies.
try {
  var stored = localStorage.getItem('zvs.theme');
  var light = stored ? JSON.parse(stored) === 'light' : matchMedia('(prefers-color-scheme: light)').matches;
  if (light) {
    document.documentElement.dataset.theme = 'light';
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', '#f5f3ee');
  }
} catch {}
