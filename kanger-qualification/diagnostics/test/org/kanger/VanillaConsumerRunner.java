package org.kanger;
/** No bridge API reference: tests the consumer jar against the actual vanilla core. */
public final class VanillaConsumerRunner {
 public static void main(String[] args){
  try{QualifiedJournalConsumer.begin();throw new AssertionError("vanilla core accepted");}
  catch(QualifiedJournalConsumer.Refusal e){if(!"NO_INSTRUMENTATION".equals(e.code))throw e;}
  System.out.println("VANILLA_CONSUMER_REFUSAL_OK");
 }
}
