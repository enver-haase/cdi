package com.vaadin.cdi;

import java.util.Set;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import com.vaadin.server.DefaultUIProvider;
import com.vaadin.server.WrappedRequest;
import com.vaadin.ui.UI;

public class CDIUIProvider extends DefaultUIProvider {

    @Inject
    private BeanManager beanManager;

    @Inject
    private BeanStoreContainer beanStoreContainer;

    @Override
    public UI createInstance(Class<? extends UI> type, WrappedRequest request) {
        Bean<?> uiBean = getUIBeanMatchingDeploymentDescriptor(type);

        if (uiBean == null) {
            if (type.isAnnotationPresent(VaadinUI.class)) {
                String uiMapping = parseUIMapping(request);
                uiBean = getUIBeanMatchingMapping(uiMapping);
            }
        }

        if (uiBean != null) {
            UI ui = (UI) beanManager.getReference(uiBean, type,
                    beanManager.createCreationalContext(uiBean));
            beanStoreContainer.uiInitialized(ui);

            return ui;
        }

        throw new RuntimeException("Could not instantiate UI");
    }

    @Override
    public Class<? extends UI> getUIClass(WrappedRequest request) {
        String UIMapping = parseUIMapping(request);
        Bean<?> uiBean = getUIBeanMatchingMapping(UIMapping);

        if (uiBean != null) {
            return uiBean.getBeanClass().asSubclass(UI.class);
        }

        // See if UI is configured to web.xml with VaadinCDIServlet
        return super.getUIClass(request);
    }

    private Bean<?> getUIBeanMatchingMapping(String mapping) {
        Set<Bean<?>> beans = beanManager.getBeans(UI.class,
                new VaadinUIAnnotation());

        for (Bean<?> bean : beans) {
            Class<? extends UI> beanClass = bean.getBeanClass().asSubclass(
                    UI.class);

            if (beanClass.isAnnotationPresent(VaadinUI.class)) {
                VaadinUI annotation = beanClass.getAnnotation(VaadinUI.class);

                if (annotation.mapping() != null) {
                    if (mapping.equals(annotation.mapping())) {
                        return bean;
                    }
                }
            }
        }

        return null;
    }

    private Bean<?> getUIBeanMatchingDeploymentDescriptor(
            Class<? extends UI> type) {

        // If @VaadinUI qualifier is not given but UI is defined in deployment
        // descriptor
        Set<Bean<?>> beans = beanManager.getBeans(type);

        if (beans.isEmpty()) {
            // Otherwise check whether UI with qualifier exists
            beans = beanManager.getBeans(type, new VaadinUIAnnotation());
        }

        if (beans.isEmpty()) {
            getLogger().warning(
                    "Could not find UI bean for " + type.getCanonicalName());
            return null;
        }

        if (beans.size() > 1) {
            getLogger().warning(
                    "Found multiple UI beans for " + type.getCanonicalName());
            return null;
        }

        return beans.iterator().next();
    }

    private String parseUIMapping(WrappedRequest request) {
        String requestPath = request.getRequestPathInfo();
        if (requestPath != null && requestPath.length() > 1) {
            if (requestPath.endsWith("/")) {
                return requestPath.substring(1, requestPath.lastIndexOf("/"));
            } else {
                return requestPath.substring(1);
            }
        }
        return "";
    }

    private static Logger getLogger() {
        return Logger.getLogger(CDIUIProvider.class.getCanonicalName());
    }
}