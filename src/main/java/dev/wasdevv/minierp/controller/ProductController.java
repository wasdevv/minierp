package dev.wasdevv.minierp.controller;

import dev.wasdevv.minierp.dto.ProductFilter;
import dev.wasdevv.minierp.dto.ProductForm;
import dev.wasdevv.minierp.exception.DuplicateSkuException;
import dev.wasdevv.minierp.exception.InUseException;
import dev.wasdevv.minierp.exception.StaleProductException;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/products")
public class ProductController {

    private static final String FORM = "products/form";
    private static final Sort LIST_ORDER = Sort.by("name", "id");

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@RequestParam(name = "q", required = false) String query,
                       @RequestParam(name = "filter", required = false) String filterParam,
                       @PageableDefault(size = 10) Pageable pageable,
                       Model model) {
        // Sorting is fixed: a client-supplied ?sort= on an unknown property would otherwise be a 500.
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LIST_ORDER);
        ProductFilter filter = ProductFilter.fromParam(filterParam);
        model.addAttribute("page", service.search(query, filter, page));
        model.addAttribute("q", query == null ? "" : query.strip());
        model.addAttribute("filter", filter);
        model.addAttribute("filters", ProductFilter.values());
        model.addAttribute("lowStock", Product.LOW_STOCK);
        return "products/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("product", new ProductForm());
        return FORM;
    }

    @PostMapping
    public String create(@Validated({Default.class, ProductForm.OnCreate.class}) @ModelAttribute("product") ProductForm form,
                         BindingResult result,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return FORM;
        }
        try {
            service.create(form);
        } catch (DuplicateSkuException e) {
            rejectDuplicateSku(result);
            return FORM;
        }
        redirect.addFlashAttribute("notice", "Produto cadastrado.");
        return "redirect:/products";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("product", ProductForm.from(service.get(id)));
        model.addAttribute("productId", id);
        return FORM;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("product") ProductForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        model.addAttribute("productId", id);
        if (result.hasErrors()) {
            service.get(id);
            return FORM;
        }
        try {
            service.update(id, form);
        } catch (DuplicateSkuException e) {
            rejectDuplicateSku(result);
            return FORM;
        } catch (StaleProductException e) {
            redirect.addFlashAttribute("alert",
                    "O produto foi alterado (por uma venda ou outra edição) depois que você abriu o formulário. "
                            + "Confira os valores atuais e salve de novo.");
            return "redirect:/products/" + id + "/edit";
        }
        redirect.addFlashAttribute("notice", "Produto atualizado.");
        return "redirect:/products";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.delete(id);
        } catch (InUseException e) {
            redirect.addFlashAttribute("alert", e.getMessage());
            return "redirect:/products";
        }
        redirect.addFlashAttribute("notice", "Produto excluído.");
        return "redirect:/products";
    }

    private static void rejectDuplicateSku(BindingResult result) {
        result.rejectValue("sku", "duplicate", "Já existe um produto com este SKU.");
    }
}
