package uk.gov.justice.digital.hmpps.visitallocationapi.batch

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Batch jobs run in short-lived pods, so remove all SQS listener beans in batch mode to stop the pod consuming queues.
 */
@Component
@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
class SqsListenerSuppressor : BeanFactoryPostProcessor {
  companion object {
    private val LOG = LoggerFactory.getLogger(this::class.java)
  }

  override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
    (beanFactory as DefaultListableBeanFactory).beanDefinitionNames
      .filter { beanName -> beanFactory.hasSqsListener(beanName) }
      .forEach {
        LOG.info("Removing SQS listener bean {} for batch job", it)
        beanFactory.removeBeanDefinition(it)
      }
  }

  private fun DefaultListableBeanFactory.hasSqsListener(beanName: String): Boolean {
    val beanClass = getBeanDefinition(beanName).beanClassName
      ?.let { runCatching { Class.forName(it, false, beanClassLoader) }.getOrNull() }
      ?: runCatching { getType(beanName, false) }.getOrNull()

    return beanClass
      ?.methods
      ?.any { it.isAnnotationPresent(SqsListener::class.java) } == true
  }
}
