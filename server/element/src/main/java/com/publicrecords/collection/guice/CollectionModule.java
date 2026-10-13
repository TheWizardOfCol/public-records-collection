package com.publicrecords.collection.guice;

import com.google.inject.PrivateModule;
import com.google.inject.Singleton;
import com.publicrecords.collection.getlit.GetLitContent;
import com.publicrecords.collection.cryptid.CryptidCinemaContent;
import com.publicrecords.collection.lobby.LobbyRegistry;

public class CollectionModule extends PrivateModule {

    @Override
    protected void configure() {

        // Content library (books, prompts) loaded from the Element archive; shared by the
        // game engine and the REST library endpoints, loaded once for the process lifetime.
        // Exposed so the exported LobbyServiceImpl/LibraryServiceImpl (bound in the root
        // injector by GuiceSpiModule) can inject it despite requireExplicitBindings().
        bind(GetLitContent.class).in(Singleton.class);
        expose(GetLitContent.class);
        bind(CryptidCinemaContent.class).in(Singleton.class);
        expose(CryptidCinemaContent.class);

        // The lobby hub holds live rooms and is shared by the REST and WebSocket
        // surfaces, so it is bound as a singleton and exposed for injection into
        // those surfaces. Exported services (CollectionService, LobbyService) are
        // bound and exposed by the SDK from their @ElementServiceImplementation
        // annotations, so they are deliberately not bound here.
        bind(LobbyRegistry.class).in(Singleton.class);
        expose(LobbyRegistry.class);
    }

}
