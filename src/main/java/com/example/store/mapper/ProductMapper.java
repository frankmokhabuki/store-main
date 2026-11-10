package com.example.store.mapper;

import com.example.store.dto.ProductDTO;
import com.example.store.entity.Order;
import com.example.store.entity.Product;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    ProductDTO productToProductDTO(Product product);

    List<ProductDTO> productsToProductDTOs(List<Product> products);

    /**
     * The ProductDTO only carries the IDs of the orders containing the product, not the full order objects - this keeps
     * the response small and avoids circular references.
     */
    default Long orderToOrderId(Order order) {
        return order == null ? null : order.getId();
    }
}
