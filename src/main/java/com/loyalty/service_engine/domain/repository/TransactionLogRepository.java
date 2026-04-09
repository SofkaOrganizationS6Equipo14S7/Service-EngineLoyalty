package com.loyalty.service_engine.domain.repository;

import com.loyalty.service_engine.domain.entity.TransactionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio para TransactionLogEntity.
 * Proporciona acceso a registros de auditoría de cálculos de descuentos.
 * Soporta búsquedas por ecommerce, periodo de tiempo y limpieza automática.
 */
@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLogEntity, UUID> {

    /**
     * Encuentra un registro de transacción por ecommerce y orden externa (única)
     */
    Optional<TransactionLogEntity> findByEcommerceIdAndExternalOrderId(UUID ecommerceId, String externalOrderId);

    /**
     * Obtiene todos los registros de transacción para un ecommerce, ordenados por fecha descendente
     */
    List<TransactionLogEntity> findByEcommerceIdOrderByCreatedAtDesc(UUID ecommerceId);

    /**
     * Obtiene registros de transacción en un rango de fechas
     */
    @Query("SELECT tl FROM TransactionLogEntity tl " +
           "WHERE tl.ecommerceId = :ecommerceId " +
           "AND tl.calculatedAt >= :startDate " +
           "AND tl.calculatedAt <= :endDate " +
           "ORDER BY tl.createdAt DESC")
    List<TransactionLogEntity> findByEcommerceIdAndDateRange(
        @Param("ecommerceId") UUID ecommerceId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );

    /**
     * Obtiene registros que necesitan limpieza (expirados)
     */
    @Query("SELECT tl FROM TransactionLogEntity tl " +
           "WHERE tl.expiresAt < CURRENT_TIMESTAMP")
    List<TransactionLogEntity> findExpiredRecords();

    /**
     * Elimina registros expirados
     */
    @Query("DELETE FROM TransactionLogEntity tl " +
           "WHERE tl.expiresAt < CURRENT_TIMESTAMP")
    void deleteExpiredRecords();

    /**
     * Verifica si un external_order_id ya existe para un ecommerce (evitar duplicados)
     */
    boolean existsByEcommerceIdAndExternalOrderId(UUID ecommerceId, String externalOrderId);
}
