package com.familyos.tag.entity;

import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 묶음(Collection)-태그 연결. UNIQUE(collection_id, tag_id, alive_uk) 는 DDL. */
@Entity
@Table(name = "collection_tag")
public class CollectionTag extends FamilyScopedEntity {

    @Column(name = "collection_id", nullable = false, updatable = false)
    private Long collectionId;

    @Column(name = "tag_id", nullable = false, updatable = false)
    private Long tagId;

    protected CollectionTag() {
    }

    public CollectionTag(Long familyId, Long collectionId, Long tagId) {
        setFamilyId(familyId);
        this.collectionId = collectionId;
        this.tagId = tagId;
    }

    public Long getCollectionId() {
        return collectionId;
    }

    public Long getTagId() {
        return tagId;
    }
}
