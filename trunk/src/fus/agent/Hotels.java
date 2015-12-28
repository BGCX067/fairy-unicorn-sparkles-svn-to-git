/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package fus.agent;

import java.util.*;
import se.sics.tac.aw.Bid;
import se.sics.tac.aw.Quote;
import se.sics.tac.aw.TACAgent;

/**
 * The module of the Fairy Unicorn Sparkles TAC Classic agent that handles the auctions for rooms in the hotels.
 * @author David Monks, dm11g08@ecs.soton.ac.uk
 */
public class Hotels {
    
    //CLASS - SINGLETON PATTERN
    
    private static Hotels singleton;
    
    private static final int custCount = 8;
    private static final int dayCount = 4;
    
    private static final float fraction = 3f/10f;
    private static final float maxfrac = 2;
    
    public static Hotels getHotelsHandler(FUSAgent impl){
        if (singleton == null){
            return singleton = new Hotels(impl);
        }else{
            return singleton;
        }
    }
    
    //INSTANCE
    
    protected final FUSAgent impl;
    
    private Bidder bidder;
    
    //The last ask price for the auction for the given hotel on the given day, -1 if closed.
    protected float[][] askPrice;
    
    private Hotels(FUSAgent i){
        this.impl = i;
        this.bidder = null;
    }
    
    public void init(){
        this.bidder = new Bidder();
        this.bidder.start();
        
        for (int x = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_CHEAP_HOTEL, 1);
                 x < TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_GOOD_HOTEL, dayCount + 1);
                 x++){
            impl.setPrice(x, 0);
        }
        
        this.askPrice = new float[2][dayCount];
        for (int x = 0; x < dayCount; x++){
            askPrice[0][x] = 0;
            askPrice[1][x] = 0;
        }
    }
    
    public void gameStopped(){
        this.bidder.interrupt();
    }
    
    public float initialBid(int auction){
        final int day = TACAgent.getAuctionDay(auction);
        final int type = TACAgent.getAuctionType(auction);
        
        HashMap<Integer, Integer> bids = new HashMap<Integer,Integer>();
        
        for (int c = 0; c < custCount; c++){
            Customer customer = impl.getCustomer(c);
            float price = 0;
            if (day >= customer.getInboundDay()
                    && day < customer.getOutboundDay()
                    && customer.bookingInHotel(type)){
                //Calculate the value lost if no room can be booked on this day.
                //No plane tickets are booked before initial bids are placed, so
                //purchases of these tickets can be ignored.
                //If the customer is staying one night then moving that day will incur
                //an effective overhead cost of 200 units.
                if (customer.wantsSingleNight()){
                    price += calculateSinglePrice(type, day, customer);

                //If the customer is staying more than one night, then removing the
                //day in question will incur an overhead cost of 100 units per day
                //lost.  As neither flight is booked, we minimise the number of days
                //potentially lost by finding whether there were more days requested
                //before or after the specified day.
                }else{
                    price += (Math.min(day - customer.getInboundDay() + 1, customer.getOutboundDay() - day)) * 100;

                }
                
                //As there is potential to buy tickets in the other hotel for the
                //same night, we wish to try and get the tickets cheap at this stage.
                //Currently using an arbitrary fraction of the base price.
                //This is in the hope that other agents think similarly, and the first
                //auction closes with low price, though it could also result in
                //frequently losing the first auction.
                price *= fraction;
                
                if (type == TACAgent.TYPE_GOOD_HOTEL){
                //If the auction is for the good hotel, offer the maximum fraction
                //of the customer's bonus available for one day on top of the base
                //price.  This and the above behaviour provides a preference
                //weighting on which hotel is selected for which customer whilst
                //trying to ensure at least breaking even with either strategy.
                    price += customer.getPerNightBonus();
                }
                
                customer.setRoomPrice(type, day, price);

                int priorCount;
                //Record the bid with complete accuracy in a matchable format.
                //The easiest way to do this is to multiply the price so that it can
                //be represented at max resolution for a float in an integer.
                int bid = (int)Math.floor(price*100);
                try{
                    priorCount = bids.get(bid);
                }catch (NullPointerException ex){
                    priorCount = 0;
                }
                bids.put(bid, priorCount+1);
            }
        }
        
        Bid bid = new Bid(auction);
        for (int multipliedBid : bids.keySet()){
            //Fetch the number of bids at each price for the auction and convert
            //the price back to a float before bidding that much on that many rooms.
            float price = ((float)multipliedBid)/(100f);
            if (price > 0)
                bid.addBidPoint(bids.get(multipliedBid), price);
        }
        impl.getAgent().submitBid(bid);
        
        return 0;
    }
    
    public void calculateClientAllocation(int inFlight, int outFlight){
        int auction;
        for (int d = inFlight; d < outFlight; d++){
            auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_CHEAP_HOTEL, d);
            impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
            auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_GOOD_HOTEL, d);
            impl.getAgent().setAllocation(auction, impl.getAgent().getAllocation(auction) + 1);
        }
    }
    
    public synchronized void calcNewBidPrice(Quote quote){
        if (!quote.isAuctionClosed()){
            //get the auction we're bidding in
            final int auction = quote.getAuction();
            final int type = TACAgent.getAuctionType(auction);
            final int day = TACAgent.getAuctionDay(auction);
            final int otherType = type == TACAgent.TYPE_CHEAP_HOTEL
                                ? TACAgent.TYPE_GOOD_HOTEL
                                : TACAgent.TYPE_CHEAP_HOTEL;
            askPrice[type][day - 1] = quote.getAskPrice();
            
            HashMap<Integer, Integer> bids = new HashMap<Integer,Integer>();

            for (int c = 0; c < custCount; c++){
                Customer customer = impl.getCustomer(c);
                float price;
                if (day >= customer.getInboundDay()
                        && day < customer.getOutboundDay()
                        && customer.bookingInHotel(type)){
                    //Calculate the value lost if no room can be booked on this day.
                    //Any plane and other hotel tickets bought must be added to the
                    //cost of not buying in this auction.
                    //If the customer is staying one night then moving that day will incur
                    //an effective overhead cost of 200 units.
                    if (customer.needsSingleNight()){
                        calculateSinglePrice(type,day,customer);

                    //If the customer is staying more than one night, then removing the
                    //day in question will incur an overhead cost of 100 units per day
                    //lost.  As neither flight is booked, we minimise the number of days
                    //potentially lost by finding whether there were more days requested
                    //before or after the specified day.
                    }else{
                        calculatePriceInPlan(type,day,customer);
                    }
                    
                    if (customer.bookingInHotel(otherType)){
                    //As long as there is potential to buy tickets in the other
                    //hotel for the same night, we wish to try and get the tickets
                    //cheap.
                    //Currently using an arbitrary fraction of the base price.
                    //This is in the hope that other agents think similarly, and the first
                    //auctions clos with a low price, though it could also result in
                    //frequently losing the early auctions.
                        price =  customer.getRoomPrice(type,day)*(fraction + (1-fraction)*impl.getGameDecisec()/(54*maxfrac));
                    }else{
                        price =  customer.getRoomPrice(type,day);
                    }
                    
                    int priorCount;
                    //Record the bid with complete accuracy in a matchable format.
                    //The easiest way to do this is to multiply the price so that it can
                    //be represented at max resolution for a float in an integer.
                    int bid = (int)Math.floor(price*100);
                    //Group bids at the same price.
                    try{
                        priorCount = bids.get(bid);
                    }catch (NullPointerException ex){
                        priorCount = 0;
                    }
                    bids.put(bid, priorCount+1);
                }
            }

            Bid bid = new Bid(auction);
            for (int multipliedBid : bids.keySet()){
                //Fetch the number of bids at each price for the auction and convert
                //the price back to a float before bidding that much on that many rooms.
                //If the calculated bid price is less than the asking price of the room,
                //do not place the bid point.
                float price = ((float)multipliedBid)/(100f);
                if (price > quote.getAskPrice())
                    bid.addBidPoint(bids.get(multipliedBid), price);
            }

            int excess;
            int hqw = quote.hasHQW(quote.getBid()) ? quote.getHQW() : 0;
            Bid lastBid = impl.getAgent().getBid(auction);
            float maxPrice = 0;
            try{
                for (int x = 0; x < lastBid.getNoBidPoints(); x++){
                    maxPrice = Math.max(maxPrice,lastBid.getPrice(x));
                }
            }catch (NullPointerException e){
                maxPrice = 0;
            }
            int bidCount;
            try{
                bidCount = lastBid.getQuantity();
            }catch (NullPointerException e){
                bidCount = 0;
            }
            
            if (bid.getQuantity() > 0 || maxPrice >= quote.getAskPrice())
                if (bid.getQuantity() == 0
                        && hqw > 0
                        && (hqw < bidCount || maxPrice > quote.getAskPrice()))
                    bid.addBidPoint(hqw, quote.getAskPrice()+1);
                else if ((excess = hqw - bid.getQuantity()) > 0)
                    bid.addBidPoint(excess, quote.getAskPrice()+1);

            if (bid.getNoBidPoints() > 0)
                bidder.submitBid(auction, bid);
        }
    }
    
    public synchronized void auctionClosed(int auction){
        final int type = TACAgent.getAuctionType(auction);
        final int day = TACAgent.getAuctionDay(auction);
        final int otherType = type == TACAgent.TYPE_CHEAP_HOTEL
                              ? TACAgent.TYPE_GOOD_HOTEL
                              : TACAgent.TYPE_CHEAP_HOTEL;
        askPrice[type][day - 1] = -1;
        
        //Initialise lists of those customers interested in tickets, those
        //granted tickets and those denied them.
        List<Customer> interested = new ArrayList<Customer>();
        List<Customer> granted = new ArrayList<Customer>();
        List<Customer> denied = new ArrayList<Customer>();
        
        for (int c = 0; c < custCount; c++){
            Customer customer = impl.getCustomer(c);
            if (customer.getInboundDay() <= day
                    && day < customer.getOutboundDay()
                    && customer.bookingInHotel(type))
                interested.add(customer);
        }
        
        if (impl.getAgent().getOwn(auction) < interested.size()){
            if (impl.getAgent().getOwn(auction) == 0){
                //If no tickets were won, add all interested customers to the denied
                //list.
                denied = interested;
            }else{
                //If some tickets were won then they are granted to those who
                //were willing to pay most for them.
                //To this end, the list of interested customers is sorted by the
                //size of the bid they made for the rooms, then divided into
                //those granted rooms and those denied them at the index equal
                //to the number of tickets won.
                Collections.sort(interested, new Comparator<Customer>(){
                    @Override
                    public int compare(Customer o1, Customer o2) {
                        return (int)Math.ceil(o2.getRoomPrice(type, day)
                                              - o1.getRoomPrice(type, day));
                    }
                });
                for (int g = 0; g < Math.min(impl.getAgent().getOwn(auction),interested.size()); g++){
                    granted.add(interested.get(g));
                }
                for (int d = impl.getAgent().getOwn(auction); d < interested.size(); d++){
                    denied.add(interested.get(d));
                }
            }
        }else{
            //If all required tickets were won, add all interested customers to
            //the granted list.
            granted = interested;
        }
        
        //For those customers granted tickets, the value of that day and hotel
        //in their price plan becomes the saving they made.
        for (Customer customer : granted){
            customer.setRoomPrice(type, day, impl.getPrice(auction)
                                             - customer.getRoomPrice(type, day));
            if (customer.bookingInHotel(otherType)){
                //if the other hotel is still an option, prevent any attempts to
                //buy for it in the future, and reduce the number of tickets
                //expected to be bought.
                customer.stopBookingHotel(otherType);
            }
        }
        for (Customer customer : denied){
            if (customer.needsSingleNight())
                //shift both flights to the next most appropriate day.
                calculateBestSingleDay(day,type,otherType,customer);
            else if (!customer.bookingInHotel(otherType)){
                if (calculatePriceInPlan(type,day,customer) == TACAgent.TYPE_INFLIGHT)
                    customer.setInboundDay(day + 1);
                else
                    customer.setOutboundDay(day);
            }else{
                //if the other hotel is still an option, prevent further attempts
                //to buy for the current hotel, and reduce the number of tickets
                //expected to be bought.
                customer.stopBookingHotel(type);
            }
        }
        reallocateTickets(type);
        
        for (int d = 1; d <= dayCount; d++){
            calcNewBidPrice(impl.getAgent().getQuote(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_CHEAP_HOTEL, d)));
            calcNewBidPrice(impl.getAgent().getQuote(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_GOOD_HOTEL, d)));
        }
    }
    
    private int calculatePriceInPlan(int type, int day, Customer customer){
        float inFlight,outFlight,pretickets=0,posttickets=0;
        int origInAuc,newInAuc,origOutAuc,newOutAuc;
        origInAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, customer.getInboundDay());
        newInAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, day + 1);
        origOutAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, customer.getOutboundDay());
        newOutAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, day);

        inFlight = impl.getPrice(origInAuc) - (impl.getAgent().getOwn(newInAuc) > impl.getAgent().getAllocation(newInAuc)
                                            ? impl.getPrice(newInAuc)
                                            : 0);
        if (inFlight == 0) inFlight = estimatePlaneCost(newInAuc) - estimatePlaneCost(origInAuc);

        for (int x = customer.getInboundDay(); x < day; x++)
            pretickets += impl.getPrice(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, x));

        outFlight = impl.getPrice(origOutAuc) - (impl.getAgent().getOwn(newOutAuc) > impl.getAgent().getAllocation(newOutAuc)
                                                ? impl.getPrice(newOutAuc)
                                                : 0);
        if (outFlight == 0) outFlight = estimatePlaneCost(newOutAuc) - estimatePlaneCost(origOutAuc);

        for (int x = day + 1; x < customer.getOutboundDay(); x++)
            posttickets += impl.getPrice(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, x));

        //If the auction is for the good hotel, offer the maximum fraction
        //of the customer's bonus available for one day on top of the base
        //price.  This and the above behaviour provides a preference
        //weighting on which hotel is selected for which customer whilst
        //trying to ensure at least breaking even with either strategy.
        float hotelBonus = type == TACAgent.TYPE_GOOD_HOTEL ? customer.getPerNightBonus() : 0;

        float precost = (day - customer.getInboundDay() + 1) * 100 + pretickets + inFlight;
        float postcost = (customer.getOutboundDay() - day) * 100 + posttickets + outFlight;

        if ((precost < postcost && day < customer.getOutboundDay() - 1) || day == customer.getInboundDay()){
            customer.setRoomPrice(type, day, (day - customer.getInboundDay() + 1) * 100 + inFlight + hotelBonus);
            return TACAgent.TYPE_INFLIGHT;
        }else{
            customer.setRoomPrice(type, day, (customer.getOutboundDay() - day) * 100 + outFlight + hotelBonus);
            return TACAgent.TYPE_OUTFLIGHT;
        }
    }
    
    private void calculateBestSingleDay(int day, int type, int otherType, Customer customer){
        float maxp = 0f;
        float p;
        int optDay = day;
        int optType = type;
        int currentType = customer.bookingInHotel(type) ? type : otherType;
        if (customer.bookingInHotel(type))
            for (int d = 1; d <= dayCount; d++)
                if (d == day && type == currentType){
                    p = calculateSinglePrice(type,d,customer);
                    if (p > maxp){
                        maxp = p;
                        optDay = d;
                        optType = type;
                    }
                }
        if (customer.bookingInHotel(otherType))
            for (int d = 1; d <= dayCount; d++)
                if (d == day && type == currentType){
                    p = calculateSinglePrice(otherType,d,customer);
                    if (p > maxp){
                        maxp = p;
                        optDay = d;
                        optType = otherType;
                    }
                }
        customer.setSingleDay(optDay, optType);
    }
    
    private float calculateSinglePrice(int type, int d, Customer customer){
        int a = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, d);
        if (askPrice[type][d - 1] >= 0
                || impl.getAgent().getOwn(a) > impl.getAgent().getAllocation(a)
                || d == customer.getInboundDay()){
            int currentType = customer.bookingInHotel(TACAgent.TYPE_CHEAP_HOTEL) ? TACAgent.TYPE_GOOD_HOTEL
                                                                                 : TACAgent.TYPE_CHEAP_HOTEL;
            
            float penalty = (Math.abs(customer.initialInFlight - d) + Math.abs(customer.initialOutFlight - d - 1))*100;
            float hotelBonus = type == TACAgent.TYPE_GOOD_HOTEL ? customer.getPerNightBonus() : 0;
            
            float inPlanes,outPlanes;
            float hotelTickets = 0;
            if (customer.getInboundDay() == d){
                int inFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, customer.getInboundDay());
                int outFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, customer.getOutboundDay());

                inPlanes = impl.getPrice(inFlightAuc) > 0
                            ? impl.getPrice(inFlightAuc)
                            : estimatePlaneCost(inFlightAuc);
                outPlanes = impl.getPrice(outFlightAuc) > 0
                            ? impl.getPrice(outFlightAuc)
                            : estimatePlaneCost(inFlightAuc);

                if (type == currentType){
                    int hotelAuc = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, currentType, customer.getInboundDay());
                    
                    if (askPrice[type][d - 1] <= 0)
                        customer.setRoomPrice(type, d, impl.getPrice(hotelAuc) + penalty + inPlanes + outPlanes - 1000 - hotelBonus);
                    else
                        customer.setRoomPrice(type, d, 1000 + hotelBonus - penalty - inPlanes - outPlanes - hotelTickets);
                    
                    return customer.getRoomPrice(type, d);
                }
            }else{
                int newInFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, d);
                int lastInFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, customer.getInboundDay());
                int newOutFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, d + 1);
                int lastOutFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, customer.getOutboundDay());

                inPlanes = (impl.getPrice(lastInFlightAuc) > 0 ? impl.getPrice(lastInFlightAuc) : estimatePlaneCost(lastInFlightAuc))
                            - (impl.getAgent().getOwn(newInFlightAuc) > impl.getAgent().getAllocation(newInFlightAuc)
                                ? impl.getPrice(newInFlightAuc)
                                : 0);
                outPlanes = (impl.getPrice(lastOutFlightAuc) > 0 ? impl.getPrice(lastOutFlightAuc) : estimatePlaneCost(lastOutFlightAuc))
                            - (impl.getAgent().getOwn(newOutFlightAuc) > impl.getAgent().getAllocation(newOutFlightAuc)
                                ? impl.getPrice(newOutFlightAuc)
                                : 0);
            }

            int origHotelAuc = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, currentType, customer.getInboundDay());
            int newHotelAuc = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, d);
            hotelTickets = (customer.getRoomPrice(type, customer.getInboundDay()) <= 0
                               ? impl.getPrice(origHotelAuc)
                               : 0) - (impl.getAgent().getOwn(newHotelAuc) > impl.getAgent().getAllocation(newHotelAuc)
                                       ? impl.getPrice(newHotelAuc)
                                       : 0);
            
            customer.setRoomPrice(type, d, 1000 + hotelBonus - penalty - inPlanes - outPlanes - hotelTickets);

            return customer.getRoomPrice(type, d);
        }
        return 0;
    }
        
    public void reallocateTickets(final int type){
        boolean allocationMade = false;
        for (int x = 1; x <= dayCount; x++){
            final int d = x;
            int tempAuc = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, d);
            if (impl.getAgent().getOwn(tempAuc) > impl.getAgent().getAllocation(tempAuc)){
                List<Customer> wanting = new ArrayList<Customer>();
                
                //Find all plans that could profitably extend their stay backward
                int newFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, d);
                int origFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, d + 1);
                if (impl.getPrice(origFlightAuc) < 100
                        || impl.getAgent().getOwn(newFlightAuc) > impl.getAgent().getAllocation(newFlightAuc)){
                    for (int c = 0; c < custCount; c++){
                        Customer customer = impl.getCustomer(c);
                        //attempt to allocate any tickets made redundant to those who have
                        //been denied tickets and for whom it would be profitable.
                        if (d == customer.getInboundDay() - 1
                                && !customer.wantsSingleNight()
                                && d >= customer.initialInFlight
                                && customer.bookingInHotel(type)){
                            calculatePriceInPlan(type,d,customer);
                            wanting.add(customer);
                        }
                    }
                }
                
                //Find all plans that could profitably extend their stay forward
                newFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, d + 1);
                origFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, d);
                if (impl.getPrice(origFlightAuc) < 100
                        || impl.getAgent().getOwn(newFlightAuc) > impl.getAgent().getAllocation(newFlightAuc)){
                    for (int c = 0; c < custCount; c++){
                        Customer customer = impl.getCustomer(c);
                        //attempt to allocate any tickets made redundant to those who have
                        //been denied tickets and for whom it would be profitable.
                        if (d == customer.getOutboundDay()
                                && !customer.wantsSingleNight()
                                && d < customer.initialOutFlight
                                && customer.bookingInHotel(type)){
                            calculatePriceInPlan(type,d,customer);
                            wanting.add(customer);
                        }
                    }
                }
                
                //Find all single-day-stay plans that could move their stay to
                //this day, recalculaing the price they would pay for it relative
                //to their current target day.
                for (int c = 0; c < custCount; c++){
                    Customer customer = impl.getCustomer(c);
                    if (customer.needsSingleNight()){
                        calculateSinglePrice(type,d,customer);
                        wanting.add(customer);
                    }
                }
                
                //Sort those who could profit by how much they stand to gain.
                Collections.sort(wanting, new Comparator<Customer>(){
                    @Override
                    public int compare(Customer o1, Customer o2) {
                        return (int)Math.ceil(o2.getRoomPrice(type, d)
                                              - o1.getRoomPrice(type, d));
                    }
                });
                
                //assign spare tickets in order of worth to the customers who stand
                //to profit from them, then reallocate flights as appropriate.
                int count = 0;
                while (impl.getAgent().getOwn(tempAuc) > impl.getAgent().getAllocation(tempAuc)
                        && count < wanting.size()){
                    Customer customer = wanting.get(count++);
                    
                    if (customer.wantsSingleNight()){
                        int newInFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, d);
                        int origInFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, customer.getInboundDay());
                        int newOutFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, d + 1);
                        int origOutFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, customer.getOutboundDay());
                        
                        if ((impl.getPrice(origInFlightAuc) < 100
                                || impl.getAgent().getOwn(newInFlightAuc) > impl.getAgent().getAllocation(newInFlightAuc))
                            && (impl.getPrice(origOutFlightAuc) < 100
                                || impl.getAgent().getOwn(newOutFlightAuc) > impl.getAgent().getAllocation(newOutFlightAuc))){
                            customer.setSingleDay(d,type);

                            allocationMade |= true;
                        }
                    }else{
                        //the current day being before the customers in flight
                        //and after its outflight is impossible
                        if (d == customer.getInboundDay() - 1){
                            newFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, d);
                            origFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, customer.getInboundDay());
                            if (impl.getPrice(origFlightAuc) < 100
                                    || impl.getAgent().getOwn(newFlightAuc) - impl.getAgent().getAllocation(newFlightAuc) > 0){
                                customer.setInboundDay(d);
                                customer.setRoomPrice(type, d, customer.getRoomPrice(type, d)
                                                                   - impl.getPrice(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, d)));
                                allocationMade |= true;
                            }
                        }
                        if (d == customer.getOutboundDay()){
                            newFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, d + 1);
                            origFlightAuc = TACAgent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, customer.getOutboundDay());
                            if (impl.getPrice(origFlightAuc) < 100
                                    || impl.getAgent().getOwn(newFlightAuc) - impl.getAgent().getAllocation(newFlightAuc) > 0){
                                customer.setOutboundDay(d + 1);
                                customer.setRoomPrice(type, d, customer.getRoomPrice(type, d)
                                                                   - impl.getPrice(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, type, d)));
                                
                                allocationMade |= true;
                            }
                        }
                    }
                }
            }
        }
        
        for (int d = 1; d <= dayCount; d++){
            calcNewBidPrice(impl.getAgent().getQuote(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_CHEAP_HOTEL, d)));
            calcNewBidPrice(impl.getAgent().getQuote(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_GOOD_HOTEL, d)));
        }
        //if (allocationMade) reallocateTickets(type);
    }
    
    public synchronized void roomsBooked(int auction, int quantity, float price){
        impl.setPrice(auction, price);
    }
    
    public synchronized void planeBooked(int auction, int quantity, float price){
        int type = TACAgent.getAuctionType(auction);
        int day = TACAgent.getAuctionDay(auction);
        Set<Integer> auctions = new HashSet<Integer>();
        if (type == TACAgent.TYPE_INFLIGHT){
            for (int x = day; x <= dayCount; x++){
                boolean goodCovered = false;
                boolean cheapCovered = false;
                for (int c = 0; c < custCount; c++){
                    Customer customer = impl.getCustomer(c);
                    if (customer.getInboundDay() == day && x < customer.getOutboundDay()){
                        if (customer.bookingInHotel(TACAgent.TYPE_CHEAP_HOTEL)){
                            cheapCovered |= auctions.add(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_CHEAP_HOTEL, x));
                        }
                        if (customer.bookingInHotel(TACAgent.TYPE_GOOD_HOTEL)){
                            goodCovered |= auctions.add(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_GOOD_HOTEL, x));
                        }
                        
                        if (goodCovered && cheapCovered) break;
                    }
                }
            }
        }else{
            day--;
            for (int x = 1; x <= day; x++){
                boolean goodCovered = false;
                boolean cheapCovered = false;
                for (int c = 0; c < custCount; c++){
                    Customer customer = impl.getCustomer(c);
                    if (customer.getOutboundDay() == day + 1 && x + 1 > customer.getInboundDay()){
                        if (customer.bookingInHotel(TACAgent.TYPE_CHEAP_HOTEL)){
                            cheapCovered |= auctions.add(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_CHEAP_HOTEL, x));
                        }
                        if (customer.bookingInHotel(TACAgent.TYPE_GOOD_HOTEL)){
                            goodCovered |= auctions.add(TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, TACAgent.TYPE_GOOD_HOTEL, x));
                        }
                        
                        if (goodCovered && cheapCovered) break;
                    }
                }
            }
        }
        
        for (int a : auctions){
            calcNewBidPrice(impl.getAgent().getQuote(a));
        }
    }
    
    private float estimatePlaneCost(int auction){
        return impl.getPlanes().getExpectedMinPrice(auction);
    }
    
    private class Bidder extends Thread{
        
        private Map<Integer,Bid> bids;
        
        public Bidder(){
            bids = new HashMap<Integer,Bid>();
        }
        
        public void submitBid(int auction, Bid bid){
            bids.put(auction, bid);
            if (impl.getGameSec() % 60 > 50){
//System.out.println("auction: " + auction);
                if (askPrice[TACAgent.getAuctionType(auction)][TACAgent.getAuctionDay(auction) - 1] >= 0){

//System.out.println("bid " + bid.getID() + ": " +bid.getBidString());
//System.out.println("replacing: "+impl.getAgent().getBid(auction).getBidString());

                    try{
                        impl.getAgent().replaceBid(impl.getAgent().getBid(auction),bid);
                    }catch (IllegalStateException e){
                        try{
                            impl.getAgent().submitBid(bid);
                        }catch (IllegalStateException ex){}
                    }
                }
            }
        }
        
        @Override
        public void run(){
//System.out.println("Bidder Starting...");
            while (true){                
                if (impl.getGameSec() % 60 >= 54
                        && impl.getGameSec() % 60 <= 58){
//System.out.println("Bidding At Second: " + (impl.getGameSec() % 60));
//System.out.println("Placing " + bids.keySet().size() + " bids");
                    for (int auction : bids.keySet()){
//System.out.println("auction: " + auction);
                        if (askPrice[TACAgent.getAuctionType(auction)][TACAgent.getAuctionDay(auction) - 1] >= 0){

//System.out.println("bid: " +bids.get(auction).getBidString());
//try{System.out.println("replacing: "+impl.getAgent().getBid(auction).getBidString());
//}catch(NullPointerException e){System.out.println("No bid to replace!");}
              try{
                                impl.getAgent().replaceBid(impl.getAgent().getBid(auction),bids.get(auction));
                            }catch (IllegalStateException e){
                                try{
                                    impl.getAgent().submitBid(bids.get(auction));
                                }catch (IllegalStateException ex){}
                            }catch (NullPointerException e){
                                try{
                                    impl.getAgent().submitBid(bids.get(auction));
                                }catch (IllegalStateException ex){}
                            }
                        }
                    }
                    bids.clear();
//                }else{
//System.out.println("At Second: " + (impl.getGameSec() % 60));
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    break;
                }
            }
System.out.println("Bidder Closing...");
        }
    }
    
}
