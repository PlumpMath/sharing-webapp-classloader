package org.apache.catalina.loader;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.CatalinaProperties;

/**
 * Extension of Tomcat's default webapp class loader which adds capability of
 * sharing classes among webapp classloaders.
 */
public class SharingWebappClassLoader extends WebappClassLoader {
	/**
	 * A queue containing all the webapp classloaders started currently.
	 */
	protected static ConcurrentLinkedQueue<SharingWebappClassLoader> webappClassLoaders = new ConcurrentLinkedQueue<SharingWebappClassLoader>();

	/**
	 * An array containing the canonical names of classes to share among webapp
	 * classloaders
	 */
	protected String[] sharedClassNames;

	/**
	 * Create a new <code>SharingWebappClassLoader</code> using the
	 * current context class loader.
	 * 
	 * @see #SharingWebappClassLoader(ClassLoader)
	 */
	public SharingWebappClassLoader() {
		super();
		sharedClassNames = CatalinaProperties.getProperty("package.shared").split(",");
	}

	/**
	 * Create a new <code>SharingWebappClassLoader</code> with the
	 * supplied class loader as parent.
	 * 
	 * @param parent the parent {@link ClassLoader} to be used
	 */
	public SharingWebappClassLoader(ClassLoader parent) {
		super(parent);
		sharedClassNames = CatalinaProperties.getProperty("package.shared", "").split(",");
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getName());
		sb.append("\r\n");
		sb.append(super.toString());
		return sb.toString();
	}

	/**
	 * Start the class loader.
	 * 
	 * @exception LifecycleException if a lifecycle error occurs
	 */
	public void start() throws LifecycleException {
		synchronized (webappClassLoaders) {
			super.start();
			webappClassLoaders.add(this);
		}
	}

	/**
	 * Stop the class loader.
	 * 
	 * @exception LifecycleException if a lifecycle error occurs
	 */
	public void stop() throws LifecycleException {
		synchronized (webappClassLoaders) {
			webappClassLoaders.remove(this);
			super.stop();
		}
	}

	/**
	 * Load the class with the specified name, searching using the following
	 * algorithm until it finds and returns the class. If the class cannot be
	 * found, returns <code>ClassNotFoundException</code>.
	 * <ul>
	 * <li>Call <code>findLoadedClass(String)</code> to check if the class has
	 * already been loaded. If it has, the same <code>Class</code> object is
	 * returned.</li>
	 * <li>If the <code>delegate</code> property is set to <code>true</code>,
	 * call the <code>loadClass()</code> method of the parent class loader, if
	 * any.</li>
	 * <li>Call <code>findLoadedClass()</code> on all other webapp classloaders
	 * to find this class in their locally defined repositories if class name is
	 * contained in Catalina property 'package.shared'.</li>
	 * <li>Call <code>findClass()</code> to find this class in our locally
	 * defined repositories.</li>
	 * <li>Call the <code>loadClass()</code> method of our parent class loader,
	 * if any.</li>
	 * </ul>
	 * If the class was found using the above steps, and the
	 * <code>resolve</code> flag is <code>true</code>, this method will then
	 * call <code>resolveClass(Class)</code> on the resulting Class object.
	 * 
	 * @param name Name of the class to be loaded
	 * @param resolve If <code>true</code> then resolve the class
	 * 
	 * @exception ClassNotFoundException if the class was not found
	 */
	public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

		synchronized (name.intern()) {
			if (log.isDebugEnabled())
				log.debug("loadClass(" + name + ", " + resolve + ")");
			Class<?> clazz = null;

			// Log access to stopped classloader
			if (!started) {
				try {
					throw new IllegalStateException();
				} catch (IllegalStateException e) {
					log.info(sm.getString("webappClassLoader.stopped", name), e);
				}
			}

			// (0) Check our previously loaded local class cache
			clazz = findLoadedClass0(name);
			if (clazz != null) {
				if (log.isDebugEnabled())
					log.debug("  Returning class from cache");
				if (resolve)
					resolveClass(clazz);
				return (clazz);
			}

			// (0.1) Check our previously loaded class cache
			clazz = findLoadedClass(name);
			if (clazz != null) {
				if (log.isDebugEnabled())
					log.debug("  Returning class from cache");
				if (resolve)
					resolveClass(clazz);
				return (clazz);
			}

			// (0.2) Try loading the class with the system class loader, to
			// prevent
			// the webapp from overriding J2SE classes
			try {
				clazz = system.loadClass(name);
				if (clazz != null) {
					if (resolve)
						resolveClass(clazz);
					return (clazz);
				}
			} catch (ClassNotFoundException e) {
				// Ignore
			}

			// (0.5) Permission to access this class when using a
			// SecurityManager
			if (securityManager != null) {
				int i = name.lastIndexOf('.');
				if (i >= 0) {
					try {
						securityManager.checkPackageAccess(name.substring(0, i));
					} catch (SecurityException se) {
						String error = "Security Violation, attempt to use " + "Restricted Class: "
								+ name;
						log.info(error, se);
						throw new ClassNotFoundException(error, se);
					}
				}
			}

			boolean delegateLoad = delegate || filter(name);

			// (1) Delegate to our parent if requested
			if (delegateLoad) {
				if (log.isDebugEnabled())
					log.debug("  Delegating to parent classloader1 " + parent);
				ClassLoader loader = parent;
				if (loader == null)
					loader = system;
				try {
					clazz = loader.loadClass(name);
					if (clazz != null) {
						if (log.isDebugEnabled())
							log.debug("  Loading class from parent");
						if (resolve)
							resolveClass(clazz);
						return (clazz);
					}
				} catch (ClassNotFoundException e) {
					;
				}
			}

			boolean sharedLoad = false;
			for (String sharedClassName : sharedClassNames) {
				if (sharedClassName.length() > 0 && name.startsWith(sharedClassName)) {
					sharedLoad = true;
					break;
				}
			}

			if (sharedLoad) {
				// (SharingWebappClassLoader extra) search another webapp classloaders
				synchronized (webappClassLoaders) {
					Iterator<SharingWebappClassLoader> classLoaderIterator = webappClassLoaders
							.iterator();
					while (classLoaderIterator.hasNext()) {
						SharingWebappClassLoader anotherClassLoader = classLoaderIterator.next();
						if (anotherClassLoader == this)
							continue;

						clazz = anotherClassLoader.findLoadedClass0(name);
						if (clazz != null) {
							if (log.isDebugEnabled())
								log.debug("  Returning class from cache of another webapp classloader: "
										+ anotherClassLoader);
							if (resolve)
								anotherClassLoader.resolveClass(clazz);
							return (clazz);
						}

						clazz = anotherClassLoader.findLoadedClass(name);
						if (clazz != null) {
							if (log.isDebugEnabled())
								log.debug("  Returning class from cache of another webapp classloader: "
										+ anotherClassLoader);
							if (resolve)
								anotherClassLoader.resolveClass(clazz);
							return (clazz);
						}
					}

					// (2) Search local repositories
					if (log.isDebugEnabled())
						log.debug("  Searching local repositories");
					try {
						clazz = findClass(name);
						if (clazz != null) {
							if (log.isDebugEnabled())
								log.debug("  Loading class from local repository");
							if (resolve)
								resolveClass(clazz);
							return (clazz);
						}
					} catch (ClassNotFoundException e) {
						;
					}
				}
			} else {
				// (2) Search local repositories
				if (log.isDebugEnabled())
					log.debug("  Searching local repositories");
				try {
					clazz = findClass(name);
					if (clazz != null) {
						if (log.isDebugEnabled())
							log.debug("  Loading class from local repository");
						if (resolve)
							resolveClass(clazz);
						return (clazz);
					}
				} catch (ClassNotFoundException e) {
					;
				}
			}

			// (3) Delegate to parent unconditionally
			if (!delegateLoad) {
				if (log.isDebugEnabled())
					log.debug("  Delegating to parent classloader at end: " + parent);
				ClassLoader loader = parent;
				if (loader == null)
					loader = system;
				try {
					clazz = loader.loadClass(name);
					if (clazz != null) {
						if (log.isDebugEnabled())
							log.debug("  Loading class from parent");
						if (resolve)
							resolveClass(clazz);
						return (clazz);
					}
				} catch (ClassNotFoundException e) {
					;
				}
			}

			throw new ClassNotFoundException(name);
		}
	}
}
