package tajo.engine.planner.physical;

import tajo.catalog.Column;
import tajo.catalog.Schema;
import tajo.catalog.SchemaUtil;
import tajo.SubqueryContext;
import tajo.engine.exec.eval.EvalContext;
import tajo.engine.exec.eval.EvalNode;
import tajo.engine.planner.PlannerUtil;
import tajo.engine.planner.Projector;
import tajo.engine.planner.logical.JoinNode;
import tajo.storage.FrameTuple;
import tajo.storage.Tuple;
import tajo.storage.VTuple;

import java.io.IOException;
import java.util.*;

/**
 * @author Byung Nam Lim
 * @author Hyunsik Choi
 */
public class HashJoinExec extends PhysicalExec {
  // from logical plan
  private JoinNode plan;
  private Schema inSchema;
  private Schema outSchema;
  private EvalNode joinQual;

  // sub operations
  private PhysicalExec outer;
  private PhysicalExec inner;

  private List<Column[]> joinKeyPairs;

  // temporal tuples and states for nested loop join
  private boolean first = true;
  private FrameTuple frameTuple;
  private Tuple outTuple = null;
  private Map<Tuple, List<Tuple>> tupleSlots;
  private Iterator<Tuple> iterator = null;
  private EvalContext qualCtx;
  private Tuple outerTuple;
  private Tuple outerKeyTuple;

  private int [] outerKeyList;
  private int [] innerKeyList;

  private boolean finished = false;
  boolean nextOuter = true;

  // projection
  private final Projector projector;
  private final EvalContext [] evalContexts;

  public HashJoinExec(SubqueryContext ctx, JoinNode joinNode, PhysicalExec outer,
      PhysicalExec inner) {
    this.outer = outer;
    this.inner = inner;
    this.plan = joinNode;
    this.inSchema = SchemaUtil.merge(outer.getSchema(), inner.getSchema());
    this.outSchema = joinNode.getOutputSchema();
    this.joinQual = joinNode.getJoinQual();
    this.qualCtx = joinQual.newContext();
    this.tupleSlots = new HashMap<Tuple, List<Tuple>>(10000);

    this.joinKeyPairs = PlannerUtil.getJoinKeyPairs(joinQual,
        outer.getSchema(), inner.getSchema());

    outerKeyList = new int[joinKeyPairs.size()];
    innerKeyList = new int[joinKeyPairs.size()];

    for (int i = 0; i < joinKeyPairs.size(); i++) {
      outerKeyList[i] = outer.getSchema().getColumnId(joinKeyPairs.get(i)[0].getQualifiedName());
    }

    for (int i = 0; i < joinKeyPairs.size(); i++) {
      innerKeyList[i] = inner.getSchema().getColumnId(joinKeyPairs.get(i)[1].getQualifiedName());
    }

    // for projection
    this.projector = new Projector(inSchema, outSchema, joinNode.getTargets());
    this.evalContexts = projector.renew();

    // for join
    frameTuple = new FrameTuple();
    outTuple = new VTuple(outSchema.getColumnNum());
    outerKeyTuple = new VTuple(outerKeyList.length);
  }

  private void getKeyOuterTuple(final Tuple outerTuple, Tuple keyTuple) {
    for (int i = 0; i < outerKeyList.length; i++) {
      keyTuple.put(i, outerTuple.get(outerKeyList[i]));
    }
  }

  public Tuple next() throws IOException {
    if (first) {
      loadInnerTable();
    }

    Tuple innerTuple;
    boolean found = false;

    while(!finished) {

      if (nextOuter) {
        // getting new outer
        outerTuple = outer.next();
        if (outerTuple == null) {
          finished = true;
          return null;
        }

        // getting corresponding inner
        getKeyOuterTuple(outerTuple, outerKeyTuple);
        if (tupleSlots.containsKey(outerKeyTuple)) {
          iterator = tupleSlots.get(outerKeyTuple).iterator();
          nextOuter = false;
        } else {
          nextOuter = true;
          continue;
        }
      }

      // getting next inner tuple
      innerTuple = iterator.next();
      frameTuple.set(outerTuple, innerTuple);
      joinQual.eval(qualCtx, inSchema, frameTuple);
      if (joinQual.terminate(qualCtx).asBool()) {
        projector.eval(evalContexts, frameTuple);
        projector.terminate(evalContexts, outTuple);
        found = true;
      }

      if (!iterator.hasNext()) { // no more inner tuple
        nextOuter = true;
      }

      if (found) {
        break;
      }
    }

    return outTuple;
  }

  private void loadInnerTable() throws IOException {
    Tuple tuple;
    Tuple keyTuple;

    while ((tuple = inner.next()) != null) {
      keyTuple = new VTuple(joinKeyPairs.size());
      List<Tuple> newValue;
      for (int i = 0; i < innerKeyList.length; i++) {
        keyTuple.put(i, tuple.get(innerKeyList[i]));
      }

      if (tupleSlots.containsKey(keyTuple)) {
        newValue = tupleSlots.get(keyTuple);
        newValue.add(tuple);
        tupleSlots.put(keyTuple, newValue);
      } else {
        newValue = new ArrayList<Tuple>();
        newValue.add(tuple);
        tupleSlots.put(keyTuple, newValue);
      }
    }
    first = false;
  }

  @Override
  public Schema getSchema() {
    return outSchema;
  }

  @Override
  public void rescan() throws IOException {
    outer.rescan();
    inner.rescan();

    tupleSlots.clear();
    first = true;

    finished = false;
    iterator = null;
    nextOuter = true;
  }

  public PhysicalExec getOuter() {
    return this.outer;
  }

  public PhysicalExec getInner() {
    return this.inner;
  }

  public JoinNode getPlan() {
    return this.plan;
  }
}