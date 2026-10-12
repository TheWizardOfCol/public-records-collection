package com.publicrecords.collection.guice;

import com.google.inject.PrivateModule;
import com.publicrecords.collection.service.CollectionService;
import com.publicrecords.collection.service.CollectionServiceImpl;

public class CollectionModule extends PrivateModule {

    @Override
    protected void configure() {

        bind(CollectionService.class).to(CollectionServiceImpl.class);

        expose(CollectionService.class);
    }

}
