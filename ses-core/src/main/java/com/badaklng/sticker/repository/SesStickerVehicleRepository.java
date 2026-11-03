package com.badaklng.sticker.repository;

import com.badaklng.sticker.entity.SesStickerVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesStickerVehicleRepository extends JpaRepository<SesStickerVehicle, Long> {

    List<SesStickerVehicle> findByStickerRequestIdOrderByLineNoAsc(Long stickerRequestId);

    long countByStickerRequestId(Long stickerRequestId);

    /**
     * Vehicles for many sticker requests in one query — the bulk form needed
     * by {@code StickerPaymentDescriptor.rowData}, which otherwise walks
     * {@link #findByStickerRequestIdOrderByLineNoAsc} once per list row.
     * Chunk the ids with {@code InChunks} (Oracle's 1000-expression cap).
     */
    List<SesStickerVehicle> findByStickerRequestIdIn(List<Long> stickerRequestIds);

    void deleteByStickerRequestId(Long stickerRequestId);

    boolean existsByStickerTypeCode(String stickerTypeCode);

    List<SesStickerVehicle> findTop5ByDriverBadgeOrderByStickerVehicleIdDesc(String driverBadge);

    List<SesStickerVehicle> findTop5ByDriverNikOrderByStickerVehicleIdDesc(String driverNik);
}
