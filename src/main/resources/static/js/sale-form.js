// Live subtotals, discount, total and change on the sale form. Display only: the server ignores these numbers
// and recomputes everything from the database when the sale is registered. Without JavaScript the form still
// works and the summary updates on "Adicionar item".
(function () {
  const form = document.querySelector('form[action$="/sales"]');
  if (!form) return;
  const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  const $ = (selector) => form.querySelector(selector);

  function cents(value) {
    const n = Number(value);
    return Number.isFinite(n) ? Math.round(n * 100) : 0;
  }

  function update() {
    let subtotal = 0;
    form.querySelectorAll('.sale-item').forEach(function (row) {
      const option = row.querySelector('select').selectedOptions[0];
      const quantity = parseInt(row.querySelector('input[type="number"]').value, 10);
      const out = row.querySelector('[data-subtotal]');
      out.classList.remove('sale-item__subtotal--warn');
      if (!option || !option.dataset.price || !(quantity > 0)) {
        out.textContent = '';
        return;
      }
      const line = cents(option.dataset.price) * quantity;
      subtotal += line;
      out.textContent = money.format(line / 100);
      if (quantity > Number(option.dataset.stock)) {
        out.textContent += ' · acima do estoque';
        out.classList.add('sale-item__subtotal--warn');
      }
    });

    const raw = cents($('[data-discount]').value);
    const percent = $('[data-discount-type]').value === 'PERCENT';
    let discount = percent ? Math.round(subtotal * Math.min(raw / 100, 100) / 100) : raw;
    discount = Math.max(0, Math.min(discount, subtotal));
    const total = subtotal - discount;

    $('[data-subtotal-total]').textContent = money.format(subtotal / 100);
    $('[data-discount-total]').textContent = '− ' + money.format(discount / 100);
    $('[data-total]').textContent = money.format(total / 100);

    const method = (form.querySelector('[data-payment]:checked') || {}).value;
    const cash = method === 'CASH';
    $('[data-cash]').hidden = !cash;
    $('[data-on-account-hint]').hidden = method !== 'ON_ACCOUNT';
    const received = cents($('[data-received]').value);
    const changeRow = $('[data-change-row]');
    changeRow.hidden = !(cash && received > 0);
    if (!changeRow.hidden) {
      const change = received - total;
      $('[data-change]').textContent = change >= 0 ? money.format(change / 100) : 'falta ' + money.format(-change / 100);
      changeRow.classList.toggle('sale-summary__short', change < 0);
    }
  }

  form.addEventListener('input', update);
  form.addEventListener('change', update);
  update();
})();
