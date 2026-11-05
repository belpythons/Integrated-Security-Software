package com.badaklng.sticker.repository;

import com.badaklng.sticker.entity.SesStickerType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * No "...OrderByOrderByAsc()"-style derived method here on purpose — Spring
 * Data's keyword parser splits a method name on the literal substring
 * "OrderBy" and throws "OrderBy must not be used more than once in a
 * method name" the moment it appears twice ANYWHERE in the name, including
 * when the second occurrence is just the entity property {@code orderBy}
 * itself, not the keyword (see {@code SesStickerType.orderBy}). Callers
 * pass {@code Sort.by("orderBy")} explicitly instead — sidesteps the
 * keyword parser entirely, since {@code findAll(Sort)}/{@code
 * findByActiveFlag(String, Sort)} take Sort as a normal parameter, not
 * part of the method name.
 */
@Repository
public interface SesStickerTypeRepository extends JpaRepository<SesStickerType, String> {

    List<SesStickerType> findByActiveFlag(String activeFlag, Sort sort);
}
