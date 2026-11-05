package com.badaklng.sticker.repository;

import com.badaklng.sticker.entity.SesStickerVehDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SesStickerVehDocRepository extends JpaRepository<SesStickerVehDoc, Long> {

    List<SesStickerVehDoc> findByStickerVehicleId(Long stickerVehicleId);

    List<SesStickerVehDoc> findByStickerVehicleIdIn(Collection<Long> stickerVehicleIds);

    Optional<SesStickerVehDoc> findByStickerVehicleIdAndDocCode(Long stickerVehicleId, String docCode);

    void deleteByStickerVehicleId(Long stickerVehicleId);

    void deleteByStickerVehicleIdIn(Collection<Long> stickerVehicleIds);
}
