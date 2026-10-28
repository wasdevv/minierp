// Live subtotals and total on the sale form. Display only: the server ignores these numbers and recomputes
// everything from the database when the sale is registered. Without JavaScript the form still works and the
// total updates on "Adicionar item".
(function () {
  const form = document.querySelector('form[action$="/sales"]');
  if (!form) return;
  const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  const total = form.querySelector('[data-total]');

  function cents(price) {
    return Math.round(Number(price) * 100);
  }

  function update() {
    let sum = 0;
    form.querySelectorAll('.sale-item').forEach(function (row) {
      const option = row.querySelector('select').selectedOptions[0];
      const quantity = parseInt(row.querySelector('input[type="number"]').value, 10);
      const out = row.querySelector('[data-subtotal]');
      out.classList.remove('sale-item__subtotal--warn');
      if (!option || !option.dataset.price || !(quantity > 0)) {
        out.textContent = '';
        return;
      }
      const subtotal = cents(option.dataset.price) * quantity;
      sum += subtotal;
      out.textContent = money.format(subtotal / 100);
      if (quantity > Number(option.dataset.stock)) {
        out.textContent += ' · acima do estoque';
        out.classList.add('sale-item__subtotal--warn');
      }
    });
    if (total) total.textContent = money.format(sum / 100);
  }

  form.addEventListener('input', update);
  form.addEventListener('change', update);
  update();
})();
