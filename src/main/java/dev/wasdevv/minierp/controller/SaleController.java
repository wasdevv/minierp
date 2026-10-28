package dev.wasdevv.minierp.controller;

import dev.wasdevv.minierp.dto.SaleForm;
import dev.wasdevv.minierp.exception.SaleRejectedException;
import dev.wasdevv.minierp.model.Sale;
import dev.wasdevv.minierp.repository.CustomerRepository;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.service.SaleService;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Sales are created and viewed, never edited or deleted. The form adds and removes rows with plain
 * submit buttons, so it works without JavaScript and every step goes through the server.
 */
@Controller
@RequestMapping("/sales")
public class SaleController {

    private static final String FORM = "sales/form";
    private static final Pattern ITEM_FIELD = Pattern.compile("items\\[(\\d+)]\\.(\\w+)");
    private static final Sort LIST_ORDER = Sort.by(Sort.Order.desc("saleDate"), Sort.Order.desc("id"));

    private final SaleService service;
    private final ProductRepository products;
    private final CustomerRepository customers;

    public SaleController(SaleService service, ProductRepository products, CustomerRepository customers) {
        this.service = service;
        this.products = products;
        this.customers = customers;
    }

    @GetMapping
    public String list(@PageableDefault(size = 10) Pageable pageable, Model model) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LIST_ORDER);
        model.addAttribute("page", service.list(page));
        return "sales/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        return form(SaleForm.withEmptyRow(), List.of(), model);
    }

    @PostMapping(params = "addItem")
    public String addItem(@ModelAttribute("sale") SaleForm form, BindingResult result, Model model) {
        form.addRow();
        return form(form, List.of(), model);
    }

    @PostMapping(params = "removeItem")
    public String removeItem(@ModelAttribute("sale") SaleForm form, BindingResult result,
                             @RequestParam("removeItem") int index, Model model) {
        form.removeRow(index);
        return form(form, List.of(), model);
    }

    @PostMapping
    public String create(@ModelAttribute("sale") SaleForm form, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return form(form, bindingProblems(result), model);
        }
        Sale sale;
        try {
            sale = service.create(form);
        } catch (SaleRejectedException e) {
            return form(form, e.getReasons(), model);
        }
        redirect.addFlashAttribute("notice", "Venda registrada.");
        return "redirect:/sales/" + sale.getId();
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        model.addAttribute("sale", service.get(id));
        return "sales/show";
    }

    /** Type mismatches ("abc" as a quantity) arrive here before the service sees the form. */
    private static List<String> bindingProblems(BindingResult result) {
        return result.getFieldErrors().stream().map(error -> {
            Matcher row = ITEM_FIELD.matcher(error.getField());
            if (row.matches()) {
                String field = row.group(2).equals("quantity") ? "a quantidade deve ser um número inteiro"
                        : "escolha um produto da lista";
                return "Item " + (Integer.parseInt(row.group(1)) + 1) + ": " + field + ".";
            }
            return "Escolha um cliente da lista.";
        }).distinct().toList();
    }

    private String form(SaleForm form, List<String> problems, Model model) {
        if (form.getItems().isEmpty()) {
            form.addRow();
        }
        model.addAttribute("sale", form);
        model.addAttribute("problems", problems);
        model.addAttribute("products", products.findByActiveTrueOrderByNameAsc());
        model.addAttribute("customers", customers.findAllByOrderByNameAsc());
        model.addAttribute("estimatedTotal", service.estimateTotal(form));
        return FORM;
    }
}
