package com.wex.payments.transactions.infrastructure.persistence.repository;

import com.wex.payments.transactions.infrastructure.persistence.entity.PurchaseTransactionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseTransactionRepository extends JpaRepository<PurchaseTransactionEntity, UUID> {
}
