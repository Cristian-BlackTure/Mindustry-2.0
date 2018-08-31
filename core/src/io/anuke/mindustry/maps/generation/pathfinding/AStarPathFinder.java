package io.anuke.mindustry.maps.generation.pathfinding;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BinaryHeap;
import com.badlogic.gdx.utils.IntMap;
import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.function.Predicate;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;

//TODO
public class AStarPathFinder extends TilePathfinder{
    IntMap<NodeRecord> records = new IntMap<>();
    BinaryHeap<NodeRecord> openList;
    NodeRecord current;

    private int searchId;
    private Tile end;

    private static final byte UNVISITED = 0;
    private static final byte OPEN = 1;
    private static final byte CLOSED = 2;

    private static final boolean debug = false;

    public AStarPathFinder(Tile[][] tiles) {
        super(tiles);
        this.openList = new BinaryHeap<>();
    }

    @Override
    public void search(Tile start, Predicate<Tile> result, Array<Tile> out){

    }

    public boolean searchNodePath(Tile startNode, Tile endNode, Array<Tile> outPath) {
        this.end = endNode;

        // Perform AStar
        boolean found = search(startNode, endNode);

        if (found) {
            // Create a path made of nodes
            generateNodePath(startNode, outPath);
        }

        return found;
    }

    protected boolean search(Tile startNode, Tile endNode) {

        initSearch(startNode, endNode);

        // Iterate through processing each node
        do {
            // Retrieve the node with smallest estimated total cost from the open list
            current = openList.pop();
            current.category = CLOSED;

            // Terminate if we reached the goal node
            if (current.node == endNode) return true;

            visitChildren(endNode);

        } while (openList.size > 0);

        // We've run out of nodes without finding the goal, so there's no solution
        return false;
    }
/*
    public boolean search(PathFinderRequest<Tile> request, long timeToRun) {

        long lastTime = TimeUtils.nanoTime();

        // We have to initialize the search if the status has just changed
        if (request.statusChanged) {
            initSearch(request.startNode, request.endNode);
            request.statusChanged = false;
        }

        // Iterate through processing each node
        do {

            // Check the available time
            long currentTime = TimeUtils.nanoTime();
            timeToRun -= currentTime - lastTime;
            if (timeToRun <= PathFinderQueue.TIME_TOLERANCE) return false;

            // Retrieve the node with smallest estimated total cost from the open list
            current = openList.pop();
            current.category = CLOSED;

            // Terminate if we reached the goal node; we've found a path.
            if (current.node == request.endNode) {
                request.pathFound = true;

                generateNodePath(request.startNode, request.resultPath);

                return true;
            }

            // Visit current node's children
            visitChildren(request.endNode);

            // Store the current time
            lastTime = currentTime;

        } while (openList.size > 0);

        // The open list is empty and we've not found a path.
        request.pathFound = false;
        return true;
    }*/

    protected void initSearch(Tile startNode, Tile endNode) {

        // Increment the search id
        if (++searchId < 0) searchId = 1;

        // Initialize the open list
        openList.clear();

        // Initialize the record for the start node and add it to the open list
        NodeRecord startRecord = getNodeRecord(startNode);
        startRecord.node = startNode;
        //startRecord.connection = null;
        startRecord.costSoFar = 0;
        addToOpenList(startRecord, estimate(startNode, endNode));

        current = null;
    }

    protected void visitChildren(Tile endNode) {
        if(debug) Effects.effect(Fx.spawn, current.node.worldx(), current.node.worldy());

        nodes(current.node, node -> {
            float addCost = estimate(current.node, node);

            float nodeCost = current.costSoFar + addCost;

            float nodeHeuristic;
            NodeRecord nodeRecord = getNodeRecord(node);

            if (nodeRecord.category == CLOSED) { // The node is closed

                // If we didn't find a shorter route, skip
                if (nodeRecord.costSoFar <= nodeCost){
                    return;
                }

                // We can use the node's old cost values to calculate its heuristic
                // without calling the possibly expensive heuristic function
                nodeHeuristic = nodeRecord.getEstimatedTotalCost() - nodeRecord.costSoFar;
            } else if (nodeRecord.category == OPEN) { // The node is open

                //If our route is no better, then skip
                if (nodeRecord.costSoFar <= nodeCost){
                    return;
                }

                // Remove it from the open list (it will be re-added with the new cost)
                openList.remove(nodeRecord);

                // We can use the node's old cost values to calculate its heuristic
                // without calling the possibly expensive heuristic function
                nodeHeuristic = nodeRecord.getEstimatedTotalCost() - nodeRecord.costSoFar;
            } else { // the node is unvisited

                // We'll need to calculate the heuristic value using the function,
                // since we don't have a node record with a previously calculated value
                nodeHeuristic = estimate(node, endNode);
            }

            // Update node record's cost and connection
            nodeRecord.costSoFar = nodeCost;
            nodeRecord.from = current.node;

            // Add it to the open list with the estimated total cost
            addToOpenList(nodeRecord, nodeCost + nodeHeuristic);
        });
    }

    protected void nodes(Tile current, Consumer<Tile> cons){
        if(obstacle(current)) return;
        for(int i = 0; i < 4; i ++){
            Tile n = current.getNearby(i);
            if(!obstacle(n)) cons.accept(n);
        }
    }

    protected Tile rel(Tile tile, int i){
        return tile.getNearby(Geometry.d8[Mathf.mod(i, 8)]);
    }

    protected boolean obstacle(Tile tile){
        return tile == null || (tile.solid() && end.target() != tile && tile.target() != end);
    }

    protected float estimate(Tile tile, Tile other){
        return Math.abs(tile.worldx() - other.worldx()) + Math.abs(tile.worldy() - other.worldy());
        // (tile.occluded ? tilesize : 0) + (other.occluded ? tilesize : 0);
    }

    protected void generateNodePath(Tile startNode, Array<Tile> outPath) {

        // Work back along the path, accumulating nodes
        // outPath.clear();
        while (current.from != null) {
            outPath.add(current.node);
            current = records.get(indexOf(current.from));
        }
        outPath.add(startNode);

        // Reverse the path
        outPath.reverse();
    }

    protected void addToOpenList(NodeRecord nodeRecord, float estimatedTotalCost) {
        openList.add(nodeRecord, estimatedTotalCost);
        nodeRecord.category = OPEN;
    }

    protected NodeRecord getNodeRecord(Tile node) {
        if(!records.containsKey(indexOf(node))){
            NodeRecord record = new NodeRecord();
            record.node = node;
            record.searchId = searchId;
            records.put(indexOf(node), record);
            return record;
        }else{
            NodeRecord record =  records.get(indexOf(node));
            if(record.searchId != searchId){
                record.category = UNVISITED;
                record.searchId = searchId;
            }
            return record;
        }
    }

    private int indexOf(Tile node){
        return node.packedPosition();
    }

    static class NodeRecord extends BinaryHeap.Node {
        Tile node;
        Tile from;

        float costSoFar;
        byte category;

        int searchId;

        public NodeRecord() {
            super(0);
        }

        public float getEstimatedTotalCost() {
            return getValue();
        }
    }
}