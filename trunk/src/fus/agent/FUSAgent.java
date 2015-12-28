/**
 * TAC AgentWare
 * http://www.sics.se/tac        tac-dev@sics.se
 *
 * Copyright (c) 2001-2005 SICS AB. All rights reserved.
 *
 * SICS grants you the right to use, modify, and redistribute this
 * software for noncommercial purposes, on the conditions that you:
 * (1) retain the original headers, including the copyright notice and
 * this text, (2) clearly document the difference between any derived
 * software and the original, and (3) acknowledge your use of this
 * software in pertaining publications and reports.  SICS provides
 * this software "as is", without any warranty of any kind.  IN NO
 * EVENT SHALL SICS BE LIABLE FOR ANY DIRECT, SPECIAL OR INDIRECT,
 * PUNITIVE, INCIDENTAL OR CONSEQUENTIAL LOSSES OR DAMAGES ARISING OUT
 * OF THE USE OF THE SOFTWARE.
 *
 * -----------------------------------------------------------------
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 23 April, 2002
 * Updated : $Date: 2005/06/07 19:06:16 $
 *	     $Revision: 1.1 $
 * ---------------------------------------------------------
 * DummyAgent is a simplest possible agent for TAC. It uses
 * the TACAgent agent ware to interact with the TAC server.
 *
 * Important methods in TACAgent:
 *
 * Retrieving information about the current Game
 * ---------------------------------------------
 * int getGameID()
 *  - returns the id of current game or -1 if no game is currently plaing
 *
 * getServerTime()
 *  - returns the current server time in milliseconds
 *
 * getGameTime()
 *  - returns the time from start of game in milliseconds
 *
 * getGameTimeLeft()
 *  - returns the time left in the game in milliseconds
 *
 * getGameLength()
 *  - returns the game length in milliseconds
 *
 * int getAuctionNo()
 *  - returns the number of auctions in TAC
 *
 * int getClientPreference(int client, int type)
 *  - returns the clients preference for the specified type
 *   (types are TACAgent.{ARRIVAL, DEPARTURE, HOTEL_VALUE, E1, E2, E3}
 *
 * int getAuctionFor(int category, int type, int day)
 *  - returns the auction-id for the requested resource
 *   (categories are TACAgent.{CAT_FLIGHT, CAT_HOTEL, CAT_ENTERTAINMENT
 *    and types are TACAgent.TYPE_INFLIGHT, TACAgent.TYPE_OUTFLIGHT, etc)
 *
 * int getAuctionCategory(int auction)
 *  - returns the category for this auction (CAT_FLIGHT, CAT_HOTEL,
 *    CAT_ENTERTAINMENT)
 *
 * int getAuctionDay(int auction)
 *  - returns the day for this auction.
 *
 * int getAuctionType(int auction)
 *  - returns the type for this auction (TYPE_INFLIGHT, TYPE_OUTFLIGHT, etc).
 *
 * int getOwn(int auction)
 *  - returns the number of items that the agent own for this
 *    auction
 *
 * Submitting Bids
 * ---------------------------------------------
 * void submitBid(Bid)
 *  - submits a bid to the tac server
 *
 * void replaceBid(OldBid, Bid)
 *  - replaces the old bid (the current active bid) in the tac server
 *
 *   Bids have the following important methods:
 *    - create a bid with new Bid(AuctionID)
 *
 *   void addBidPoint(int quantity, float price)
 *    - adds a bid point in the bid
 *
 * Help methods for remembering what to buy for each auction:
 * ----------------------------------------------------------
 * int getAllocation(int auctionID)
 *   - returns the allocation set for this auction
 * void setAllocation(int auctionID, int quantity)
 *   - set the allocation for this auction
 *
 *
 * Callbacks from the TACAgent (caused via interaction with server)
 *
 * bidUpdated(Bid bid)
 *  - there are TACAgent have received an answer on a bid query/submission
 *   (new information about the bid is available)
 * bidRejected(Bid bid)
 *  - the bid has been rejected (reason is bid.getRejectReason())
 * bidError(Bid bid, int error)
 *  - the bid contained errors (error represent error status - commandStatus)
 *
 * quoteUpdated(Quote quote)
 *  - new information about the quotes on the auction (quote.getAuction())
 *    has arrived
 * quoteUpdated(int category)
 *  - new information about the quotes on all auctions for the auction
 *    category has arrived (quotes for a specific type of auctions are
 *    often requested at once).

 * auctionClosed(int auction)
 *  - the auction with id "auction" has closed
 *
 * transaction(Transaction transaction)
 *  - there has been a transaction
 *
 * gameStarted()
 *  - a TAC game has started, and all information about the
 *    game is available (preferences etc).
 *
 * gameStopped()
 *  - the current game has ended
 *
 */

package fus.agent;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import se.sics.tac.util.ArgEnumerator;
import java.util.logging.*;
import se.sics.tac.aw.AgentImpl;
import se.sics.tac.aw.Bid;
import se.sics.tac.aw.Quote;
import se.sics.tac.aw.TACAgent;
import se.sics.tac.aw.Transaction;

public class FUSAgent extends AgentImpl {

  private static final Logger log =
    Logger.getLogger(FUSAgent.class.getName());
  
  public static Logger getLog(){
      return log;
  }

  private static final boolean DEBUG = false;


  private float[] prices;

  private Entertainment entertainment;
  private Hotels hotels;
  private Planes planes;

  ArrayList<float[][]> askHistory;
  float[][] askAverage;

  
  private List<Customer> customers;
  
  protected long starttime;
    private int gameCount;

  public TACAgent getAgent(){return agent;}
  
  public Customer getCustomer(int index){return customers.get(index);}
  
  public synchronized void setPrice(int auction, float price){
      prices[auction] = price;
  }
  
  public synchronized float getPrice(int auction){
      return prices[auction];
  }
  
  public Planes getPlanes(){
      return planes;
  }
  
  public Hotels getHotels(){
      return hotels;
  }
  
  protected void init(ArgEnumerator args) {
    prices = new float[agent.getAuctionNo()];
    agent.clearAllocation();
    customers = new ArrayList<Customer>();
    
    entertainment = new Entertainment(this);

    hotels = Hotels.getHotelsHandler(this);
    planes = Planes.getPlanesHandler(this);
    askHistory = new ArrayList<float[][]>();      
    askAverage = new float[agent.getAuctionNo()][18];         
    
  }

  public void quoteUpdated(Quote quote) {
    int auction = quote.getAuction();
    int auctionCategory = agent.getAuctionCategory(auction);
    if (auctionCategory == TACAgent.CAT_HOTEL) {

      hotels.calcNewBidPrice(quote);

    } else if (auctionCategory == TACAgent.CAT_ENTERTAINMENT) {
//        Bid bid = new Bid(auction);
        
//        int alloc = agent.getAllocation(auction) - agent.getOwn(auction);
        entertainment.calcNewBidPrice(quote);
        
//        bid.addBidPoint(alloc, bidPrice);

//        agent.submitBid(bid);
//            if (DEBUG) {
//                log.finest("submitting bid with alloc="
//                            + agent.getAllocation(auction)
//                            + " own=" + agent.getOwn(auction));
//            }

    } else if (auctionCategory == TACAgent.CAT_FLIGHT) {
        planes.quoteUpdated(quote);
    }    
  }

    @Override
  public void quoteUpdated(int auctionCategory) {
    log.fine("All quotes for "
	     + agent.auctionCategoryToString(auctionCategory)
	     + " has been updated");    
  }

    @Override
  public void bidUpdated(Bid bid) {
    log.fine("Bid Updated: id=" + bid.getID() + " auction="
	     + bid.getAuction() + " state="
	     + bid.getProcessingStateAsString());
    log.fine("       Hash: " + bid.getBidHash());
  }

    @Override
  public void bidRejected(Bid bid) {
    log.warning("Bid Rejected: " + bid.getID());
    log.warning("      Reason: " + bid.getRejectReason()
		+ " (" + bid.getRejectReasonAsString() + ')');
  }

    @Override
  public void bidError(Bid bid, int status) {
    log.warning("Bid Error in auction " + bid.getAuction() + ": " + status
		+ " (" + agent.commandStatusToString(status) + ')');
  }

    @Override
  public void gameStarted() {
    log.fine("Game " + agent.getGameID() + " started!");

    gameCount++;
    askHistory.add(new float[agent.getAuctionNo()][18]);

    
    hotels.init();
    prices = new float[agent.getAuctionNo()];
    agent.clearAllocation();
    customers = new ArrayList<Customer>();



    //set start time
    starttime = System.currentTimeMillis() - agent.getGameTime();

    //initialize subagents
    hotels.init();
    planes.initialize();
    
    calculateAllocation();
    sendBids();
  }

    @Override
  public void gameStopped() {
    log.fine("Game Stopped!");
    System.out.println("Writing to file." + askAverage.length);
    float[][] askAverage = entertainment.getAskAverage();
    float[][] bidHistory = entertainment.getBidHistory();
    float[][] askHistory = entertainment.getAskHistory();
    for(int i=16; i < 28; i++){        
        
        float[] currAuctionAvg = askAverage[i];
        float[] currAuctionAsk = askHistory[i];
        float[] currAuctionBid = bidHistory[i];

        String outAuction = ""+currAuctionAvg[0];
        for(int step = 1; step < 18; step++){
            outAuction+= ","+currAuctionAvg[step];          
        }
        String outAskHistory = ""+currAuctionAsk[0];
        for(int step = 1; step < 18; step++){
            outAskHistory+= ","+currAuctionAsk[step];          
        }        
        String outBidHistory = ""+currAuctionBid[0];
        for(int step = 1; step < 18; step++){
            outBidHistory+= ","+currAuctionBid[step];          
        }        
//        System.out.println(outAuction);
        outAuction+="\n";
        outAskHistory+="\n";
        outBidHistory+="\n";
            try {
                FileWriter avgStream = new FileWriter("askAverage.txt",true);
                FileWriter askStream = new FileWriter("askHistory.txt", true);
                FileWriter bidStream = new FileWriter("bidHistory.txt", true);
                BufferedWriter outAvg = new BufferedWriter(avgStream);
                BufferedWriter outAsk = new BufferedWriter(askStream);
                BufferedWriter outBid = new BufferedWriter(bidStream);

                outAvg.write(outAuction);
                outAsk.write(outAskHistory);
                outBid.write(outBidHistory);
                
                outAvg.close();
                outAsk.close();
                outBid.close();
            } catch (IOException ex) {
                Logger.getLogger(FUSAgent.class.getName()).log(Level.SEVERE, null, ex);
            }
    }
    this.entertainment.updateKnowledge();

    hotels.gameStopped();


    //finalize subagents
    hotels.gameStopped();
    planes.finalize();

  }
  
    @Override
  public void auctionClosed(int auction) {
    log.fine("*** Auction " + auction + " closed!");
    switch (TACAgent.getAuctionCategory(auction)){
        case TACAgent.CAT_HOTEL:
            hotels.auctionClosed(auction);
            break;
        default:
    }
  }
    
//  private void gameEnds() {
//
//  }
  
    @Override
  public void transaction(Transaction t){
      super.transaction(t);
      
      if (TACAgent.getAuctionCategory(t.getAuction()) == TACAgent.CAT_HOTEL){
          hotels.roomsBooked(t.getAuction(),t.getQuantity(),t.getPrice());
      }
      if (TACAgent.getAuctionCategory(t.getAuction()) == TACAgent.CAT_FLIGHT){
          planes.ticketBooked(t.getAuction(),t.getQuantity(),t.getPrice());
          hotels.planeBooked(t.getAuction(),t.getQuantity(),t.getPrice());
      }
  }

  public int getGameSec() { return (int) agent.getGameTime() / 1000; }
  public int getGameDecisec() { return getGameSec() / 10; }
  
  public ArrayList<float[][]> getAskHistory() {
      if(this.askHistory == null){
          this.askHistory = new ArrayList<float[][]>();
          this.askHistory.add(new float[agent.getAuctionNo()][18]);
      }
        return this.askHistory;
  }
  
  public float getGameCount() {
      if(this.gameCount==0){
          log.warning("Game Count was 0");
          this.gameCount = 1;
      }
        return this.gameCount;
    }
  
  public float[][] getAskAverage() {
        return this.askAverage;
  }
  
  public List getCustomers() {
        return this.customers;
  }

  /**
   * sendBids is called once at the start of the game
   * to set initial prices
   */
  private void sendBids() {
    for (int i = 0, n = agent.getAuctionNo(); i < n; i++) {
      int alloc = agent.getAllocation(i) - agent.getOwn(i);
      float price = -1f;
      switch (agent.getAuctionCategory(i)) {
      case TACAgent.CAT_FLIGHT:
//	price = planes.initialBid(i);
	break;
      case TACAgent.CAT_HOTEL:
	price = hotels.initialBid(i);
	break;
      case TACAgent.CAT_ENTERTAINMENT:
//	price = entertainment.initialBid(i);
	break;
      default:
	break;
      }
      if (price > 0) {
	Bid bid = new Bid(i);
	bid.addBidPoint(alloc, price);
	if (DEBUG) {
	  log.finest("submitting bid with alloc=" + agent.getAllocation(i)
		     + " own=" + agent.getOwn(i));
	}
	agent.submitBid(bid);
      }
    }
  }

  private void calculateAllocation() {
    for (int i = 0; i < 8; i++) {
      int inFlight = agent.getClientPreference(i, TACAgent.ARRIVAL);
      int outFlight = agent.getClientPreference(i, TACAgent.DEPARTURE);

      customers.add(new Customer(this,
                                 inFlight,
                                 outFlight,
                                 agent.getClientPreference(i, TACAgent.HOTEL_VALUE),
                                 agent.getClientPreference(i, TACAgent.E1),
                                 agent.getClientPreference(i, TACAgent.E2),
                                 agent.getClientPreference(i, TACAgent.E3),
                                 4,
                                 i)
                   );
      
      // Get the flight preferences auction and remember that we are
      // going to buy tickets for these days. (inflight=1, outflight=0)
//      int auction = agent.getAuctionFor(TACAgent.CAT_FLIGHT,
//					TACAgent.TYPE_INFLIGHT, inFlight);
//      agent.setAllocation(auction, agent.getAllocation(auction) + 1);
//      auction = agent.getAuctionFor(TACAgent.CAT_FLIGHT,
//				    TACAgent.TYPE_OUTFLIGHT, outFlight);
//      agent.setAllocation(auction, agent.getAllocation(auction) + 1);

//        calculateEntertainmentAllocations(inFlight, outFlight, i);
      int eType = -1;
//      while((eType = nextEntType(i, eType)) > 0) {
//	int auction = bestEntDay(inFlight, outFlight, eType);        
//        customers.get(i).setEntertainmentPricePlan(eType-1, auction % 4, 1);
//	log.finer("Adding entertainment " + eType + " on " + auction);
//	agent.setAllocation(auction, agent.getAllocation(auction) + 1);
//      }
    }
  }
  
  public void calculateEntertainmentAllocations(int inFlight, int outFlight, int customer){
      int eType = -1;
      while((eType = nextEntType(customer, eType)) > 0) {
	int auction = bestEntDay(inFlight, outFlight, eType);
        customers.get(customer).setEntertainmentPricePlan(eType-1, (auction +1) % 4, 1);
	log.finer("Adding entertainment " + eType + " on " + auction);
	agent.setAllocation(auction, agent.getAllocation(auction) + 1);
      }       
  }
  
  private int bestEntDay(int inFlight, int outFlight, int type) {
    for (int i = inFlight; i < outFlight; i++) {
      int auction = agent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT,
					type, i);
      if (agent.getAllocation(auction) < agent.getOwn(auction)) {
	return auction;
      }
    }
    // If no left, just take the first...
    return agent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT,
			       type, inFlight);
  }

  private int nextEntType(int client, int lastType) {
    int e1 = agent.getClientPreference(client, TACAgent.E1);
    int e2 = agent.getClientPreference(client, TACAgent.E2);
    int e3 = agent.getClientPreference(client, TACAgent.E3);

    // At least buy what each agent wants the most!!!
    if ((e1 > e2) && (e1 > e3) && lastType == -1)
      return TACAgent.TYPE_ALLIGATOR_WRESTLING;
    if ((e2 > e1) && (e2 > e3) && lastType == -1)
      return TACAgent.TYPE_AMUSEMENT;
    if ((e3 > e1) && (e3 > e2) && lastType == -1)
      return TACAgent.TYPE_MUSEUM;
    return -1;
  }


  // -------------------------------------------------------------------
  // Only for backward compability
  // -------------------------------------------------------------------

  public static void main (String[] args) {
    TACAgent.main(args);
  }

  

    

 
} // DummyAgent


