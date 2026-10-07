package com.roboleague.api.config;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Runs every use case call in one database transaction, so a use case that saves two aggregates (an appeal and its
 * attempt) saves both or neither. The use cases stay plain Java: the composition root wraps each one in a proxy, the
 * same way a decorator would, and rolls back on any exception they throw.
 */
@Component
class TransactionalUseCases implements BeanPostProcessor {
    private static final String USE_CASES = "com.roboleague.usecase";

    private final ObjectProvider<PlatformTransactionManager> transactionManager;

    TransactionalUseCases(ObjectProvider<PlatformTransactionManager> transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!bean.getClass().getPackageName().equals(USE_CASES)) {
            return bean;
        }
        ProxyFactory proxy = new ProxyFactory(bean);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager.getObject(),
                new MatchAlwaysTransactionAttributeSource()));
        return proxy.getProxy(bean.getClass().getClassLoader());
    }
}
