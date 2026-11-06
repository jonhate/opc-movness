import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.*;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.*;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
public final class ResolveTests {
  public static void main(String[] args)throws Exception {
    DefaultServiceLocator locator=MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class,BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class,FileTransporterFactory.class);
    locator.addService(TransporterFactory.class,HttpTransporterFactory.class);
    locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler(){public void serviceCreationFailed(Class<?> type,Class<?> impl,Throwable error){error.printStackTrace();}});
    RepositorySystem system=locator.getService(RepositorySystem.class);if(system==null)throw new IllegalStateException("Maven Resolver unavailable");
    DefaultRepositorySystemSession session=MavenRepositorySystemUtils.newSession();
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session,new LocalRepository(args[0])));
    session.setIgnoreArtifactDescriptorRepositories(true);session.setSystemProperties(System.getProperties());
    CollectRequest request=new CollectRequest();
    request.setRepositories(Arrays.asList(new RemoteRepository.Builder("central-runtime-transport","default",args[2]+"/central").build(),new RemoteRepository.Builder("google-runtime-transport","default",args[2]+"/google").build()));
    request.addDependency(new Dependency(new DefaultArtifact("org.robolectric:robolectric:4.14.1"),"runtime"));
    request.addDependency(new Dependency(new DefaultArtifact("junit:junit:4.13.2"),"runtime"));
    DependencyResult result=system.resolveDependencies(session,new DependencyRequest(request,null));
    Path out=Paths.get(args[1]);Files.createDirectories(out);
    for(ArtifactResult artifact:result.getArtifactResults()){
      Path source=artifact.getArtifact().getFile().toPath();Files.write(out.resolve(source.getFileName()),Files.readAllBytes(source));
    }
    System.out.println("Resolved "+result.getArtifactResults().size()+" native UI test artifacts");
  }
}
