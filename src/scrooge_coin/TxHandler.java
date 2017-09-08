package scrooge_coin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {

	private UTXOPool utxoPool;
	
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
    	// Return false if transaction is null
    	if (tx == null ) {
    		return false;
    	}
    	
    	Set<Integer> checkUnique = new HashSet<Integer>(); 
    	double totalInValue = 0;
    	for(int index=0; index < tx.numInputs(); index++) {
    		Transaction.Input input = tx.getInput(index);
    		UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
    		
    		//Check this utxo exist in pool : Case-1
    		if(!utxoPool.contains(utxo)) {
    			return false;
    		}
    		
    		//Duplicate utxo in input, double spend : Case-3
    		if(checkUnique.contains(utxo.hashCode())) {
    			return false;
    		}
    		checkUnique.add(utxo.hashCode());
    		
    		Transaction.Output userOutput = utxoPool.getTxOutput(utxo);
    		//Signature not match for a user : Case-2
    		if(!Crypto.verifySignature(userOutput.address, tx.getRawDataToSign(index), input.signature)) {
    			return false;
    		}
    		totalInValue += userOutput.value;
    	}
    	
    	double totalOutValue = 0;
    	for(int index = 0; index < tx.numOutputs(); index++) {
    		Transaction.Output output = tx.getOutput(index);
    		// Output value is negative : Case-4
    		if (output.value < 0) {
    			return false;
    		}
    		totalOutValue += output.value;
    	}
    	
    	//OutValue is greater than to InValue : Case-5
    	if(totalInValue < totalOutValue) {
    		return false;
    	}
    	
    	return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
    	//Check for input value and return
        if(possibleTxs == null) {
        	return null;
        }
        
       List<Transaction> validTxs = new ArrayList<Transaction>();
       List<Transaction> invalidTxs = new ArrayList<Transaction>();
       for(int index = 0; index < possibleTxs.length; index++) {
    	   Transaction tx = possibleTxs[index];
    	   if(isValidTx(tx)) {
    		   updateTransactionIntoThePool(tx);
    		   validTxs.add(tx);
    	   } else {
    		   invalidTxs.add(tx);
    	   }
       }
  
       while(true) {
    	   //No invalid transaction left
    	   if(invalidTxs.size() == 0) {
    		   break;
    	   }
    	   
    	  
    	   List<Transaction> localInValidTxs = new ArrayList<Transaction>();
    	   boolean isFoundValidTx = false;
    	   for(Transaction tx : invalidTxs) {
        	   if(isValidTx(tx)) {
        		   updateTransactionIntoThePool(tx);
        		   validTxs.add(tx);
        		   isFoundValidTx = true;
        	   } else {
        		   localInValidTxs.add(tx);
        	   }
           }
    	   
    	   if(!isFoundValidTx) {
    		   break;
    	   }
    	 
    	   invalidTxs = localInValidTxs;
       }
      
       return validTxs.toArray(new Transaction[validTxs.size()]);
    }
    
    private void updateTransactionIntoThePool(Transaction tx) {
     	   tx.finalize();
     	   byte[] hash = tx.getHash();
     	   
     	   for(Transaction.Input input : tx.getInputs()) {
     		   UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
     		   utxoPool.removeUTXO(utxo);
     	   }
     	   
     	   for(int index = 0; index < tx.numOutputs(); index++) {
        			Transaction.Output output = tx.getOutput(index);
        			UTXO utxo = new UTXO(hash, index);
        			utxoPool.addUTXO(utxo, output);
     	   }
    }

}
