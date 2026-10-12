package com.publicrecords.collection.service;

import dev.getelements.elements.sdk.annotation.ElementServiceExport;

@ElementServiceExport(CollectionService.class)
public class CollectionServiceImpl implements CollectionService {

    private static final String ELEMENT_ID = "public-records-collection";

    @Override
    public String getElementId() {
        return ELEMENT_ID;
    }

}
