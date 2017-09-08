package scrooge_coin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MaxFeeTxHandler {

	private UTXOPool utxoPool;
	
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
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
    	return isValidTx(utxoPool, tx);
    }
    
    /**
     * Validate a transaction in the given utxoPool
     * @param utxoPool
     * @param tx
     * @return true or false
     */
    private boolean isValidTx(UTXOPool utxoPool, Transaction tx) {
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
    	
    	List<Transaction> globalValidTxs = new ArrayList<Transaction>();
    	
        List<Transaction> validTxs = new ArrayList<Transaction>();
        List<Transaction> invalidTxs = new ArrayList<Transaction>();
        for(int index = 0; index < possibleTxs.length; index++) {
     	   Transaction tx = possibleTxs[index];
     	   if(isValidTx(tx)) {
     		   validTxs.add(tx);
     	   } else {
     		   invalidTxs.add(tx);
     	   }
        }
   
        while(true) {
        	//No valid transactions
        	if(validTxs.isEmpty()) {
        		break;
        	}
        	
        	Transaction maxTx = getAndUpdateMaxProfitTransactionIntoThePool(utxoPool, validTxs, invalidTxs);
        	//Transaction is null
        	if(maxTx == null) {
        		break;
        	}
        	globalValidTxs.add(maxTx);
        	
     	   //No invalid transaction left
     	   if(invalidTxs.isEmpty()) {
     		   break;
     	   }
     	   
     	  List<Transaction> localValidTxs = new ArrayList<Transaction>();
          List<Transaction> localInvalidTxs = new ArrayList<Transaction>();
          for(Transaction tx : invalidTxs) {
       	   if(isValidTx(tx)) {
       		   localValidTxs.add(tx);
       	   } else {
       		   localInvalidTxs.add(tx);
       	   }
          }
         
          validTxs = localValidTxs;
          invalidTxs = localInvalidTxs;
        }
       
        return globalValidTxs.toArray(new Transaction[globalValidTxs.size()]);
    }
    
    /**
     * Update the transaction in the given pool
     * @param utxoPool
     * @param tx
     */
    private void updateTransactionIntoThePool(UTXOPool utxoPool, Transaction tx) {
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
    
    private Transaction getAndUpdateMaxProfitTransactionIntoThePool(UTXOPool utxoPool, 
    		List<Transaction> validTxs,
    		List<Transaction> invalidTxs) {
    	
    	int maxTransactionIndex = 0;
    	double currentMaxProfit = 0.0;
    	int validTxsSize = validTxs.size();
    	for(int index=0; index < validTxsSize; index++) {
    		Transaction tx = validTxs.get(index);
    		double profit = getProfitForTransaction(utxoPool, tx);
    		if(currentMaxProfit <= profit) {
    			maxTransactionIndex = index;
    			currentMaxProfit = profit;
    		}
    	}
    	
    	Transaction resultTx = null;
    	for(int index=0; index < validTxsSize; index++) {
    		Transaction tx = validTxs.get(index);
    		if(index == maxTransactionIndex) {
    			updateTransactionIntoThePool(utxoPool, tx);
    			resultTx = tx;
    		} else {
    			invalidTxs.add(tx);
    		}
    	}
    	return resultTx;
    }

    
    /**
     * Caller need to be ensure that transaction is valid for given pool
     * Get total profit from the transaction corresponding to given utxoPool
     * @param utxoPool
     * @param tx
     * @return total profit from the transaction
     */
    private double getProfitForTransaction(UTXOPool utxoPool, Transaction tx) {
    	double totalInValue = 0;
    	for(int index=0; index < tx.numInputs(); index++) {
    		Transaction.Input input = tx.getInput(index);
    		UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
    		Transaction.Output userOutput = utxoPool.getTxOutput(utxo);
    		totalInValue += userOutput.value;
    	}
    	
    	double totalOutValue = 0;
    	for(int index = 0; index < tx.numOutputs(); index++) {
    		Transaction.Output output = tx.getOutput(index);
    		totalOutValue += output.value;
    	}
    	double totalProfit = totalInValue - totalOutValue;
    	
    	return totalProfit; 
    }
}
