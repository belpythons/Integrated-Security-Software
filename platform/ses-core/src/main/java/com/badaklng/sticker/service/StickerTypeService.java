package com.badaklng.sticker.service;

import com.badaklng.sticker.entity.SesStickerType;
import com.badaklng.sticker.repository.SesStickerTypeRepository;
import com.badaklng.sticker.repository.SesStickerVehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD for the "Jenis Sticker" master admin screen. */
@Service
@RequiredArgsConstructor
public class StickerTypeService {

    private final SesStickerTypeRepository typeRepository;
    private final SesStickerVehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    public List<SesStickerType> findAll() {
        return typeRepository.findAll(Sort.by("orderBy"));
    }

    @Cacheable(value = "stickerTypes")
    @Transactional(readOnly = true)
    public List<SesStickerType> findActive() {
        return typeRepository.findByActiveFlag("Y", Sort.by("orderBy"));
    }

    @Transactional(readOnly = true)
    public SesStickerType findByCode(String code) {
        return typeRepository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Jenis Sticker not found: " + code));
    }

    @Transactional(readOnly = true)
    public boolean isInUse(String code) {
        return vehicleRepository.existsByStickerTypeCode(code);
    }

    @CacheEvict(value = "stickerTypes", allEntries = true)
    @Transactional
    public SesStickerType save(SesStickerType type, boolean isCreate) {
        if (type.getStickerTypeCode() == null || type.getStickerTypeCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Kode Jenis Sticker wajib diisi.");
        }
        type.setStickerTypeCode(type.getStickerTypeCode().trim().toUpperCase());
        // See RequestTypeService.saveWithDocs: save() is a merge on a natural
        // key, and the DB alone cannot distinguish a create from an update.
        if (isCreate && typeRepository.existsById(type.getStickerTypeCode())) {
            throw new IllegalArgumentException("Kode Jenis Sticker sudah digunakan: " + type.getStickerTypeCode());
        }
        return typeRepository.save(type);
    }

    @CacheEvict(value = "stickerTypes", allEntries = true)
    @Transactional
    public void delete(String code) {
        if (isInUse(code)) {
            throw new IllegalStateException("Tidak dapat menghapus: Jenis Sticker " + code + " masih digunakan pada data kendaraan.");
        }
        typeRepository.deleteById(code);
    }
}
