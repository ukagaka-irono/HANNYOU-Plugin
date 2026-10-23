package com.ukagaka.irono.hANNYOUPlugin;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

class HANNYOUPluginLoader implements PluginLoader {

    @Override
    public void classloader(final PluginClasspathBuilder builder) {
        // SQLiteドライバはjarに同梱せず、起動時にMavenミラーから取得する（jarを軽量に保つ）
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        resolver.addDependency(new Dependency(new DefaultArtifact("org.xerial:sqlite-jdbc:3.49.1.0"), null));
        builder.addLibrary(resolver);
    }
}
