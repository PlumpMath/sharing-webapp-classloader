sharing-webapp-classloader
==========================

A web application classloader for Tomcat 6.0.

Sharing-webapp-classloader is an experimental Java class loader for Tomcat 6.0. I used it to demonstrate my proposal on an alternative webapp class loading concept in Tomcat in 2010. The classloader was also applied in an R&D project.

Suppose the situation is the following. We are doing distributed software development of a web-based system with more developer groups. For the sake of the independent work every group develops its own web module (in separate software projects) and we share a part of the source code, basically interfaces and persistent entity definitions.

This concept might work well but only until one would like to share some object references between the web modules. At that point a classloading problem occurs.

Suppose one web module gets a reference to an object created in an another web module. This can be achieved for example through accessing a mediator singleton loaded by the common class loader (singleton class deployed in ${catalina.base}/lib). The problem is that the classloader of the shared object (this is the classloader of the provider web module) is different from the classloader of the consumer web module. At this point the shared object can not be casted to the corresponding type in the consumer web module, fields and methods are accessible only via reflection. This makes difficult the usage of the shared object.

One could say: why sharing references, the ambition of web containers is to fully isolate the deployed web modules. In case of a distributed architecture, it is a possible situation that a web module needs to have a reference to an object created in an another web module providing a certain service (for example a central scheduler, or an event dispatcher system). The classloading problem described above makes it difficult to use a web container this way.

There are two well-known solutions to the problem. The firts one is to use a J2EE container and deploy an enterprise module. Unfortunately, this causes unnecessary overheat. The second solution is to separate the interfaces, and all the classes they need, into a separate JAR and put it into ${catalina.base}/lib. Then, if we remove the shared classes from web applications, they are all loaded by the common classloader.

This second solution has also a serious disadvantage. The disadvantage appears in development time. The common and the shared class loaders can not reload classes. This means if any of the classes loaded by them changes the whole Tomcat server and so even all another independent web applications must be restarted for the changes to take effect. The number of shared interfaces and their dependent classes can be remarkable. When these types change, a full restart is needed instead of some web application reloads, which is supported by widely applied development tools like Web Tools Platform (WTP) Project. The full restart means extra overheat increasing the turnaround time of development cycles.

An another, only minor problem is that the deployment of the application cut into pieces must be done manually (increasing turnaround delay again). WTP for example does not support the deployment of jar files into arbitrary folders but only deployment of war files to a ‘webapps like’ folder.

Turnaround time of development cycles is a significant factor in the costs of a software solution. Accordingly, the possibility of using shared classes in a reloadable (and by development tools supported) way would be valuable in Tomcat. 

The proposal is to introduce a special web application class loader within Tomcat which can be used optionally and which is capable of sharing class objects among web modules. Classes marked as ‘shared’ in a property file would be loaded only once, by one of the web application class loaders. The other web application class loaders would use the same class objects on demand, allowing shared objects to be casted in the consumer web module.

A less elaborate, but basically working implementation of this special web application class loader is provided in this project. It is called SharingWebappClassLoader. In the constructor it reads the list of classes to share from the 'catalina.properties' file under the key 'package.shared'. If a class marked as shared should be loaded, first all other SharingWebappClassLoader instances will be checked, if they have already loaded that class. If one of those classloaders has already loaded the class then its class object will be used and returned. A shared class will be loaded only if no other SharingWebappClassLoader instance has already loaded it. Loading of not shared classes does not change, it works like in the normal WebappClassLoader.

This experimental classloader (and also the concept in this simple form) has a serious disadvantage. Once a provider webapp loads and shares a class, it can not be unloaded while another consumer webapps are depending on it. Consumer webapps however can be unloaded, reloaded. 

Tomcat developers rejected this proposal in august, 2010. Sharing-webapp-classloader was applied in an R&D pilot project successfully till 2011.
