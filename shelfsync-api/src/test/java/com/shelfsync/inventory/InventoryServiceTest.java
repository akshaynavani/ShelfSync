package com.shelfsync.inventory;

import com.shelfsync.TestFixtures;
import com.shelfsync.common.ConflictException;
import com.shelfsync.inventory.dto.CreateItemRequest;
import com.shelfsync.inventory.dto.UpdateItemRequest;
import com.shelfsync.ledger.LedgerService;
import com.shelfsync.ledger.MovementType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private final InventoryRepository repository = mock(InventoryRepository.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final InventoryService service = new InventoryService(repository, ledger);

    @Test
    void createNormalisesCategoryAndLedgersOpeningBalance() {
        when(repository.create(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryItem item = service.create(new CreateItemRequest("MUG-01", " Mug ", " Kitchen ",
                new BigDecimal("7.50"), 10, 2));

        assertThat(item.getCategory()).isEqualTo("kitchen");
        assertThat(item.getName()).isEqualTo("Mug");
        verify(ledger).record("MUG-01", MovementType.INITIAL, 10, 10, null, "Opening balance");
    }

    @Test
    void createWithZeroStockSkipsLedger() {
        when(repository.create(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new CreateItemRequest("MUG-02", "Mug", "kitchen", new BigDecimal("1.00"), 0, null));

        verify(ledger, never()).record(anyString(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void restockAppliesDeltaAndRecordsMovement() {
        when(repository.adjustStock("MUG", 5)).thenReturn(TestFixtures.item("MUG", "7.50", 12));

        InventoryItem after = service.restock("MUG", 5, "supplier delivery");

        assertThat(after.getStock()).isEqualTo(12);
        verify(ledger).record("MUG", MovementType.RESTOCK, 5, 12, null, "supplier delivery");
    }

    @Test
    void restockRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> service.restock("MUG", 0, null)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).adjustStock(anyString(), anyInt());
    }

    @Test
    void ledgerFailureRevertsTheStockChange() {
        when(repository.adjustStock("MUG", -3)).thenReturn(TestFixtures.item("MUG", "7.50", 4));
        when(ledger.record(anyString(), any(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("mysql unavailable"));

        assertThatThrownBy(() -> service.adjust("MUG", -3, "breakage")).hasMessage("mysql unavailable");

        verify(repository).adjustStock("MUG", 3);
    }

    @Test
    void updateRejectsStaleExpectedVersion() {
        InventoryItem current = TestFixtures.item("MUG", "7.50", 4);
        current.setVersion(3L);
        when(repository.getBySku("MUG")).thenReturn(current);

        assertThatThrownBy(() -> service.update("MUG", new UpdateItemRequest("New", null, null, null, 2L)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).update(any());
    }
}
