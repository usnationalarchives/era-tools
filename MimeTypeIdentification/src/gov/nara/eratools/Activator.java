package gov.nara.eratools;


import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import gov.nara.oif.api.ERATool;

public class Activator implements BundleActivator {

        private static BundleContext context;

        static BundleContext getContext() {
                return context;
        }

        /*
         * (non-Javadoc)
         * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
         */
        public void start(BundleContext bundleContext) throws Exception {
                Activator.context = bundleContext;
                context.registerService(ERATool.class.getName(), new MimeTypeTool(), null);
        }

        /*
         * (non-Javadoc)
         * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
         */
        public void stop(BundleContext bundleContext) throws Exception {
                Activator.context = null;
        }

}

