// Margin preview only; the list and the dashboard compute it on the server.
(function () {
    const price = document.querySelector('[data-price]');
    const cost = document.querySelector('[data-cost]');
    const out = document.querySelector('[data-margin]');
    function update() {
        const p = Number(price.value), c = Number(cost.value);
        out.textContent = cost.value === '' || !(p > 0) ? '' :
            'Margem: ' + ((p - c) / p * 100).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + '%';
    }
    price.addEventListener('input', update);
    cost.addEventListener('input', update);
    update();
})();
