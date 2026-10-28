package dev.wasdevv.minierp.service;

import dev.wasdevv.minierp.dto.ProductFilter;
import dev.wasdevv.minierp.dto.ProductForm;
import dev.wasdevv.minierp.exception.DuplicateSkuException;
import dev.wasdevv.minierp.exception.InUseException;
import dev.wasdevv.minierp.exception.ResourceNotFoundException;
import dev.wasdevv.minierp.exception.StaleProductException;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.repository.SaleItemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    static final String IN_USE = "Este produto já foi vendido e não pode ser excluído. Desmarque \"Ativo\" para retirá-lo das vendas.";

    private final ProductRepository products;
    private final SaleItemRepository saleItems;

    public ProductService(ProductRepository products, SaleItemRepository saleItems) {
        this.products = products;
        this.saleItems = saleItems;
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String query, ProductFilter filter, Pageable pageable) {
        if ((query == null || query.isBlank()) && filter == ProductFilter.ALL) {
            return products.findAll(pageable);
        }
        String term = query == null ? "" : SearchTerms.escapeLike(query.strip());
        return products.search(term, filter == ProductFilter.LOW_STOCK, Product.LOW_STOCK,
                filter == ProductFilter.INACTIVE, pageable);
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Transactional
    public Product create(ProductForm form) {
        if (products.existsBySku(form.getSku())) {
            throw new DuplicateSkuException(form.getSku());
        }
        Product product = new Product();
        form.applyTo(product);
        return saveUnique(product);
    }

    @Transactional
    public Product update(Long id, ProductForm form) {
        Product product = get(id);
        if (form.getVersion() == null || form.getVersion() != product.getVersion()) {
            throw new StaleProductException(id);
        }
        if (products.existsBySkuAndIdNot(form.getSku(), id)) {
            throw new DuplicateSkuException(form.getSku());
        }
        form.applyTo(product);
        return saveUnique(product);
    }

    /** Sold products stay: their sale items point at them. Deactivating is the way to retire one. */
    @Transactional
    public void delete(Long id) {
        Product product = get(id);
        if (saleItems.existsByProductId(id)) {
            throw new InUseException(IN_USE);
        }
        try {
            products.delete(product);
            products.flush();
        } catch (DataIntegrityViolationException e) {
            throw new InUseException(IN_USE);
        }
    }

    /** The exists check races with a concurrent insert; the unique constraint is what actually decides. */
    private Product saveUnique(Product product) {
        try {
            return products.saveAndFlush(product);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new StaleProductException(product.getId());
        } catch (DataIntegrityViolationException e) {
            if (isSkuConstraint(e)) {
                throw new DuplicateSkuException(product.getSku());
            }
            throw e;
        }
    }

    private static boolean isSkuConstraint(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("products_sku_key");
    }
}
