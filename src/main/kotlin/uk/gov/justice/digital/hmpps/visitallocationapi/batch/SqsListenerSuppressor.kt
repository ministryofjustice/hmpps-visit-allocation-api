package uk.gov.justice.digital.hmpps.visitallocationapi.batch

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitallocationapi.service.listener.DomainEventListener

/**
 * This batch job moves messages from the events DLQ back to the events queue. Remove the events listener in batch mode
 * so the short-lived batch pod does not consume the same messages it has just retried.
 */
@Component
@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
class SqsListenerSuppressor : BeanFactoryPostProcessor {
  companion object {
    private val LOG = LoggerFactory.getLogger(this::class.java)
  }

  override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
    (beanFactory as DefaultListableBeanFactory).beanDefinitionNames
      .filter { beanName -> beanFactory.isBeanAssignableFrom<DomainEventListener>(beanName) }
      .forEach {
        LOG.info("Removing events listener bean {} for batch job", it)
        beanFactory.removeBeanDefinition(it)
      }
  }

  private inline fun <reified T> DefaultListableBeanFactory.isBeanAssignableFrom(beanName: String): Boolean {
    getBeanDefinition(beanName).beanClassName
      ?.let { runCatching { Class.forName(it, false, beanClassLoader) }.getOrNull() }
      ?.let { return T::class.java.isAssignableFrom(it) }

    return runCatching { getType(beanName, false) }.getOrNull()
      ?.let { T::class.java.isAssignableFrom(it) } == true
  }
}
