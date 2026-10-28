// Behaviour that used to live in inline handlers (onsubmit, onclick). The Content-Security-Policy allows
// scripts from this origin only, so every page gets it from here.
document.addEventListener('submit', function (event) {
  const message = event.target.dataset && event.target.dataset.confirm;
  if (message && !window.confirm(message)) {
    event.preventDefault();
  }
});

document.addEventListener('click', function (event) {
  if (event.target.closest('[data-print]')) {
    window.print();
  }
});
