package com.shelfsync.inventory;

import com.shelfsync.inventory.dto.CreateItemRequest;
import com.shelfsync.inventory.dto.ItemResponse;
import com.shelfsync.inventory.dto.StockChangeRequest;
import com.shelfsync.inventory.dto.UpdateItemRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/items")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
        InventoryItem item = service.create(request);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{sku}").buildAndExpand(item.getSku()).toUri())
                .body(ItemResponse.from(item));
    }

    @GetMapping
    public List<ItemResponse> list(@RequestParam(required = false) String category) {
        return service.list(category).stream().map(ItemResponse::from).toList();
    }

    @GetMapping("/low-stock")
    public List<ItemResponse> lowStock() {
        return service.lowStock().stream().map(ItemResponse::from).toList();
    }

    @GetMapping("/{sku}")
    public ItemResponse get(@PathVariable String sku) {
        return ItemResponse.from(service.get(sku));
    }

    @PatchMapping("/{sku}")
    public ItemResponse update(@PathVariable String sku, @Valid @RequestBody UpdateItemRequest request) {
        return ItemResponse.from(service.update(sku, request));
    }

    @DeleteMapping("/{sku}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String sku) {
        service.delete(sku);
    }

    @PostMapping("/{sku}/restock")
    public ItemResponse restock(@PathVariable String sku, @Valid @RequestBody StockChangeRequest request) {
        return ItemResponse.from(service.restock(sku, request.quantity(), request.note()));
    }

    @PostMapping("/{sku}/adjust")
    public ItemResponse adjust(@PathVariable String sku, @Valid @RequestBody StockChangeRequest request) {
        return ItemResponse.from(service.adjust(sku, request.quantity(), request.note()));
    }
}
