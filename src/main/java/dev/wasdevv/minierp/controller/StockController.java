package dev.wasdevv.minierp.controller;

import dev.wasdevv.minierp.exception.StockRejectedException;
import dev.wasdevv.minierp.model.StockMovement;
import dev.wasdevv.minierp.model.StockMovementType;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.service.StockService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Goods in, inventory counts and the stock history. */
@Controller
@RequestMapping("/stock")
public class StockController {

    private final StockService stock;
    private final ProductRepository products;

    public StockController(StockService stock, ProductRepository products) {
        this.stock = stock;
        this.products = products;
    }

    @GetMapping
    public String index(@RequestParam(name = "productId", required = false) Long productId,
                        @RequestParam(name = "type", required = false) String typeParam,
                        @PageableDefault(size = 20) Pageable pageable,
                        Model model) {
        StockMovementType type = parseType(typeParam);
        model.addAttribute("productId", productId);
        model.addAttribute("type", type);
        model.addAttribute("types", StockMovementType.values());
        model.addAttribute("products", products.findAll(Sort.by("name", "id")));
        model.addAttribute("page", stock.history(productId, type,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())));
        if (productId != null) {
            products.findById(productId).ifPresent(product -> model.addAttribute("product", product));
        }
        return "stock/index";
    }

    @PostMapping("/receipts")
    public String receive(@RequestParam(name = "productId", required = false) Long productId,
                          @RequestParam(name = "quantity", required = false) Integer quantity,
                          @RequestParam(name = "reason", required = false) String reason,
                          RedirectAttributes redirect) {
        if (productId == null) {
            redirect.addFlashAttribute("alert", "Escolha o produto da entrada.");
            return "redirect:/stock";
        }
        try {
            StockMovement entry = stock.receive(productId, quantity, reason);
            redirect.addFlashAttribute("notice", "Entrada de " + entry.getQuantity() + " un. de "
                    + entry.getProduct().getName() + " registrada. Estoque agora: " + entry.getBalanceAfter() + ".");
        } catch (StockRejectedException e) {
            redirect.addFlashAttribute("alert", e.getMessage());
        }
        return "redirect:/stock?productId=" + productId;
    }

    @PostMapping("/adjustments")
    public String adjust(@RequestParam(name = "productId", required = false) Long productId,
                         @RequestParam(name = "counted", required = false) Integer counted,
                         @RequestParam(name = "reason", required = false) String reason,
                         RedirectAttributes redirect) {
        if (productId == null) {
            redirect.addFlashAttribute("alert", "Escolha o produto do ajuste.");
            return "redirect:/stock";
        }
        try {
            StockMovement entry = stock.adjust(productId, counted, reason);
            redirect.addFlashAttribute("notice", "Estoque de " + entry.getProduct().getName() + " ajustado para "
                    + entry.getBalanceAfter() + " (" + (entry.getQuantity() > 0 ? "+" : "") + entry.getQuantity() + ").");
        } catch (StockRejectedException e) {
            redirect.addFlashAttribute("alert", e.getMessage());
        }
        return "redirect:/stock?productId=" + productId;
    }

    private static StockMovementType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return StockMovementType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
