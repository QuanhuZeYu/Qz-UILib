package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.mock.MockPlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** U0 internal fake composition 的 executable proof。 */
public class SceneProjectionCompositionTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    @Test
    public void sameFactorySharesBusinessStateButCreatesIsolatedOccurrences() {
        Signal<String> state = Signal.create("before");
        Function<SceneRuntime, SceneNode> content = runtime -> {
            SceneNode root = new SceneNode();
            runtime.bindText(root, state);
            return root;
        };
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        try {
            SceneProjectionComposition.Occurrence first = composition.project(content, 0, 0);
            SceneProjectionComposition.Occurrence second = composition.project(content, 0, 0);
            first.runtime().flush();

            Assert.assertNotSame(first.runtime(), second.runtime());
            Assert.assertNotSame(first.root(), second.root());
            Assert.assertEquals("before", first.root().getText());
            Assert.assertEquals("before", second.root().getText());

            state.set("after");
            first.runtime().flush();

            Assert.assertEquals("after", first.root().getText());
            Assert.assertEquals("after", second.root().getText());
            Assert.assertNull(first.runtime().getFocusedNode());
            Assert.assertNull(second.runtime().getFocusedNode());
        } finally {
            composition.close();
        }
    }

    @Test
    public void drainsOnceAndDispatchesOnlyToTopMostWinner() {
        AtomicInteger buildIndex = new AtomicInteger();
        int[] clicks = new int[2];
        Function<SceneRuntime, SceneNode> content = runtime -> {
            int index = buildIndex.getAndIncrement();
            SceneNode root = new SceneNode();
            root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
            runtime.focusable(root);
            runtime.on(root, SceneEventType.CLICK, (event, context) -> clicks[index]++);
            return root;
        };
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        try {
            SceneProjectionComposition.Occurrence lower = composition.project(content, 10, 10);
            SceneProjectionComposition.Occurrence upper = composition.project(content, 10, 10);
            source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 30, 30, SceneMouseButton.LEFT);
            source.enqueuePointer(ScenePointerAction.BUTTON_UP, 30, 30, SceneMouseButton.LEFT);

            composition.drainAndRoute();

            Assert.assertEquals(1, source.drainCount());
            Assert.assertEquals(0, clicks[0]);
            Assert.assertEquals(1, clicks[1]);
            Assert.assertNull(lower.runtime().getFocusedNode());
            Assert.assertSame(upper.root(), upper.runtime().getFocusedNode());
        } finally {
            composition.close();
        }
    }

    @Test
    public void nakedTopBoundsPassAndKeyboardStaysWithFocusedWinner() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        AtomicInteger lowerKeys = new AtomicInteger();
        AtomicInteger upperKeys = new AtomicInteger();
        try {
            SceneProjectionComposition.Occurrence lower = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
                runtime.focusable(root);
                runtime.on(root, SceneEventType.CLICK, (event, context) -> { });
                runtime.on(root, SceneEventType.KEY_DOWN, (event, context) -> lowerKeys.incrementAndGet());
                return root;
            }, 0, 0);
            SceneProjectionComposition.Occurrence upper = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
                runtime.on(root, SceneEventType.KEY_DOWN, (event, context) -> upperKeys.incrementAndGet());
                return root;
            }, 0, 0);

            source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);
            source.enqueuePointer(ScenePointerAction.BUTTON_UP, 20, 20, SceneMouseButton.LEFT);
            composition.drainAndRoute();

            Assert.assertSame(lower.root(), lower.runtime().getFocusedNode());
            Assert.assertNull(upper.runtime().getFocusedNode());

            source.enqueueKey(SceneKey.ENTER, SceneKeyAction.PRESSED);
            composition.drainAndRoute();

            Assert.assertEquals(1, lowerKeys.get());
            Assert.assertEquals(0, upperKeys.get());
            Assert.assertEquals(2, source.drainCount());
        } finally {
            composition.close();
        }
    }

    @Test
    public void topPressedOnlyParticipantClaimsDownWithoutClickingThrough() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        AtomicInteger lowerClicks = new AtomicInteger();
        try {
            composition.project(runtime -> {
                SceneNode root = new SceneNode();
                root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
                runtime.on(root, SceneEventType.CLICK,
                        (event, context) -> lowerClicks.incrementAndGet());
                return root;
            }, 0, 0);
            SceneProjectionComposition.Occurrence upper = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
                runtime.interactionState(root).pressed();
                return root;
            }, 0, 0);

            source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);
            composition.drainAndRoute();
            upper.runtime().flush();
            Assert.assertEquals(Boolean.TRUE,
                    upper.runtime().interactionState(upper.root()).pressed().get());

            source.enqueuePointer(ScenePointerAction.BUTTON_UP, 20, 20, SceneMouseButton.LEFT);
            composition.drainAndRoute();
            upper.runtime().flush();
            Assert.assertEquals(Boolean.FALSE,
                    upper.runtime().interactionState(upper.root()).pressed().get());
            Assert.assertEquals(0, lowerClicks.get());
        } finally {
            composition.close();
        }
    }

    @Test
    public void programmaticFocusIsCollectedByTheSingleKeyboardOwner() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        AtomicInteger keys = new AtomicInteger();
        try {
            SceneProjectionComposition.Occurrence occurrence = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                runtime.interactionState(root).focused();
                runtime.focusable(root);
                runtime.on(root, SceneEventType.KEY_DOWN, (event, context) -> keys.incrementAndGet());
                return root;
            }, 0, 0);
            occurrence.runtime().requestFocus(occurrence.root());

            source.enqueueKey(SceneKey.ENTER, SceneKeyAction.PRESSED);
            composition.drainAndRoute();

            Assert.assertEquals(1, keys.get());
        } finally {
            composition.close();
        }
    }

    @Test
    public void initialTabSelectsOnlyTheTopMostFocusableOccurrence() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        try {
            SceneProjectionComposition.Occurrence lower = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                runtime.focusable(root);
                return root;
            }, 0, 0);
            SceneProjectionComposition.Occurrence upper = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                runtime.focusable(root);
                return root;
            }, 0, 0);

            source.enqueueKey(SceneKey.TAB, SceneKeyAction.PRESSED);
            composition.drainAndRoute();

            Assert.assertNull(lower.runtime().getFocusedNode());
            Assert.assertSame(upper.root(), upper.runtime().getFocusedNode());
        } finally {
            composition.close();
        }
    }

    @Test
    public void initialTabUsesTheTopOverlayFocusScope() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        try {
            SceneProjectionComposition.Occurrence occurrence = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                SceneNode overlay = new SceneNode();
                runtime.focusable(overlay);
                runtime.getOverlayHost().register(overlay, OverlayDismissPolicy.DEFAULT, () -> { });
                return root;
            }, 0, 0);
            SceneNode overlay = occurrence.runtime().getOverlayHost().topFirst().get(0).getRoot();

            source.enqueueKey(SceneKey.TAB, SceneKeyAction.PRESSED);
            composition.drainAndRoute();

            Assert.assertSame(overlay, occurrence.runtime().getFocusedNode());
        } finally {
            composition.close();
        }
    }

    @Test
    public void translatedOccurrenceAddsItsPlacementToOverlayHitAndLocalCoordinates() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        int[] local = {-1, -1};
        try {
            composition.project(runtime -> {
                SceneNode root = new SceneNode();
                SceneNode overlay = new SceneNode();
                overlay.setCachedLayout(new LayoutBox(0, 0, 20, 20));
                runtime.on(overlay, SceneEventType.POINTER_DOWN, (event, context) -> {
                    local[0] = context.getLocalPointerX();
                    local[1] = context.getLocalPointerY();
                });
                runtime.getOverlayHost().register(overlay);
                runtime.getOverlayHost().topFirst().get(0).setAnchorX(10);
                runtime.getOverlayHost().topFirst().get(0).setAnchorY(12);
                return root;
            }, 50, 40);

            source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 65, 57, SceneMouseButton.LEFT);
            composition.drainAndRoute();

            Assert.assertArrayEquals(new int[] {5, 5}, local);
        } finally {
            composition.close();
        }
    }

    @Test
    public void capturedOverlayUsesItsCurrentAnchorInsideATranslatedOccurrence() {
        CountingInputSource source = new CountingInputSource(240, 160);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        int[] moveLocal = {-1, -1};
        try {
            SceneProjectionComposition.Occurrence occurrence = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                SceneNode overlay = new SceneNode();
                overlay.setCachedLayout(new LayoutBox(0, 0, 20, 20));
                runtime.on(overlay, SceneEventType.POINTER_DOWN,
                        (event, context) -> context.requestPointerCapture());
                runtime.on(overlay, SceneEventType.POINTER_MOVE, (event, context) -> {
                    moveLocal[0] = context.getLocalPointerX();
                    moveLocal[1] = context.getLocalPointerY();
                });
                runtime.getOverlayHost().register(overlay);
                runtime.getOverlayHost().topFirst().get(0).setAnchorX(10);
                runtime.getOverlayHost().topFirst().get(0).setAnchorY(12);
                return root;
            }, 50, 40);

            source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 65, 57, SceneMouseButton.LEFT);
            composition.drainAndRoute();
            occurrence.runtime().getOverlayHost().topFirst().get(0).setAnchorX(20);
            occurrence.runtime().getOverlayHost().topFirst().get(0).setAnchorY(22);
            source.enqueuePointer(ScenePointerAction.MOVE, 75, 67, SceneMouseButton.NONE);
            composition.drainAndRoute();

            Assert.assertArrayEquals(new int[] {5, 5}, moveLocal);
        } finally {
            composition.close();
        }
    }

    @Test
    public void topEscapeDismissBeatsALowerFocusedKeyboardOwner() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        AtomicInteger lowerKeys = new AtomicInteger();
        AtomicInteger dismisses = new AtomicInteger();
        try {
            SceneProjectionComposition.Occurrence lower = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                runtime.focusable(root);
                runtime.on(root, SceneEventType.KEY_DOWN, (event, context) -> lowerKeys.incrementAndGet());
                return root;
            }, 0, 0);
            composition.project(runtime -> {
                SceneNode root = new SceneNode();
                runtime.getOverlayHost().register(new SceneNode(), OverlayDismissPolicy.DEFAULT,
                        dismisses::incrementAndGet);
                return root;
            }, 0, 0);
            lower.runtime().requestFocus(lower.root());

            source.enqueueKey(SceneKey.ESCAPE, SceneKeyAction.PRESSED);
            composition.drainAndRoute();

            Assert.assertEquals(1, dismisses.get());
            Assert.assertEquals(0, lowerKeys.get());
            Assert.assertSame(lower.root(), lower.runtime().getFocusedNode());
        } finally {
            composition.close();
        }
    }

    @Test
    public void movingBetweenOccurrencesClearsThePreviousHoverOwner() {
        CountingInputSource source = new CountingInputSource(240, 120);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        try {
            Function<SceneRuntime, SceneNode> hoverContent = runtime -> {
                SceneNode root = new SceneNode();
                root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
                runtime.interactionState(root).hovered();
                return root;
            };
            SceneProjectionComposition.Occurrence first = composition.project(hoverContent, 0, 0);
            SceneProjectionComposition.Occurrence second = composition.project(hoverContent, 120, 0);

            source.enqueuePointer(ScenePointerAction.MOVE, 20, 20, SceneMouseButton.NONE);
            composition.drainAndRoute();
            first.runtime().flush();
            Assert.assertEquals(Boolean.TRUE, first.runtime().interactionState(first.root()).hovered().get());
            Assert.assertEquals(Boolean.FALSE, second.runtime().interactionState(second.root()).hovered().get());

            source.enqueuePointer(ScenePointerAction.MOVE, 140, 20, SceneMouseButton.NONE);
            composition.drainAndRoute();
            first.runtime().flush();
            Assert.assertEquals(Boolean.FALSE, first.runtime().interactionState(first.root()).hovered().get());
            Assert.assertEquals(Boolean.TRUE, second.runtime().interactionState(second.root()).hovered().get());
        } finally {
            composition.close();
        }
    }

    @Test
    public void gestureUpReconcilesHoverWithoutDispatchingSyntheticMove() {
        CountingInputSource source = new CountingInputSource(240, 120);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        AtomicInteger moves = new AtomicInteger();
        try {
            SceneProjectionComposition.Occurrence first = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
                runtime.interactionState(root).hovered();
                runtime.on(root, SceneEventType.POINTER_DOWN, (event, context) -> { });
                runtime.on(root, SceneEventType.POINTER_MOVE, (event, context) -> moves.incrementAndGet());
                return root;
            }, 0, 0);
            SceneProjectionComposition.Occurrence second = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
                runtime.interactionState(root).hovered();
                return root;
            }, 120, 0);

            source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);
            composition.drainAndRoute();
            source.enqueuePointer(ScenePointerAction.MOVE, 140, 20, SceneMouseButton.NONE);
            composition.drainAndRoute();
            source.enqueuePointer(ScenePointerAction.BUTTON_UP, 140, 20, SceneMouseButton.LEFT);
            composition.drainAndRoute();
            first.runtime().flush();

            Assert.assertEquals(1, moves.get());
            Assert.assertEquals(Boolean.FALSE, first.runtime().interactionState(first.root()).hovered().get());
            Assert.assertEquals(Boolean.TRUE, second.runtime().interactionState(second.root()).hovered().get());
        } finally {
            composition.close();
        }
    }

    @Test
    public void secondaryButtonRemainsSwallowedUntilItsOwnUp() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        AtomicInteger downs = new AtomicInteger();
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger ups = new AtomicInteger();
        try {
            composition.project(runtime -> {
                SceneNode root = new SceneNode();
                root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
                runtime.on(root, SceneEventType.POINTER_DOWN, (event, context) -> downs.incrementAndGet());
                runtime.on(root, SceneEventType.POINTER_MOVE, (event, context) -> moves.incrementAndGet());
                runtime.on(root, SceneEventType.POINTER_UP, (event, context) -> ups.incrementAndGet());
                return root;
            }, 0, 0);

            source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);
            composition.drainAndRoute();
            source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.RIGHT);
            composition.drainAndRoute();
            source.enqueuePointer(ScenePointerAction.BUTTON_UP, 20, 20, SceneMouseButton.LEFT);
            composition.drainAndRoute();
            source.enqueuePointer(ScenePointerAction.MOVE, 20, 20, SceneMouseButton.NONE);
            composition.drainAndRoute();

            Assert.assertEquals(1, downs.get());
            Assert.assertEquals(0, moves.get());
            Assert.assertEquals(1, ups.get());

            source.enqueuePointer(ScenePointerAction.BUTTON_UP, 20, 20, SceneMouseButton.RIGHT);
            composition.drainAndRoute();
            source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);
            composition.drainAndRoute();
            Assert.assertEquals(2, downs.get());
        } finally {
            composition.close();
        }
    }

    @Test
    public void secondaryButtonDefersHoverReconcileUntilItsOwnUp() {
        CountingInputSource source = new CountingInputSource(240, 120);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        try {
            SceneProjectionComposition.Occurrence first = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
                runtime.interactionState(root).hovered();
                runtime.on(root, SceneEventType.POINTER_DOWN, (event, context) -> { });
                return root;
            }, 0, 0);
            SceneProjectionComposition.Occurrence second = composition.project(runtime -> {
                SceneNode root = new SceneNode();
                root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
                runtime.interactionState(root).hovered();
                return root;
            }, 120, 0);

            source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);
            source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.RIGHT);
            source.enqueuePointer(ScenePointerAction.MOVE, 140, 20, SceneMouseButton.NONE);
            source.enqueuePointer(ScenePointerAction.BUTTON_UP, 140, 20, SceneMouseButton.LEFT);
            composition.drainAndRoute();
            first.runtime().flush();

            Assert.assertEquals(Boolean.FALSE, first.runtime().interactionState(first.root()).hovered().get());
            Assert.assertEquals(Boolean.FALSE, second.runtime().interactionState(second.root()).hovered().get());

            source.enqueuePointer(ScenePointerAction.MOVE, 20, 20, SceneMouseButton.NONE);
            source.enqueuePointer(ScenePointerAction.BUTTON_UP, 20, 20, SceneMouseButton.RIGHT);
            composition.drainAndRoute();
            first.runtime().flush();

            Assert.assertEquals(Boolean.TRUE, first.runtime().interactionState(first.root()).hovered().get());
            Assert.assertEquals(Boolean.FALSE, second.runtime().interactionState(second.root()).hovered().get());
        } finally {
            composition.close();
        }
    }

    @Test
    public void closeCancelsAnActiveGestureBeforeDisposingOccurrence() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        AtomicInteger cancels = new AtomicInteger();
        SceneProjectionComposition.Occurrence occurrence = composition.project(runtime -> {
            SceneNode root = new SceneNode();
            root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
            runtime.on(root, SceneEventType.POINTER_DOWN, (event, context) -> { });
            runtime.on(root, SceneEventType.POINTER_CANCEL, (event, context) -> cancels.incrementAndGet());
            return root;
        }, 0, 0);
        source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);
        composition.drainAndRoute();

        composition.close();

        Assert.assertEquals(1, cancels.get());
        Assert.assertNull(occurrence.runtime().getFocusedNode());
    }

    @Test
    public void closeIsReentrantAndCancelIsDeliveredExactlyOnce() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        AtomicInteger cancels = new AtomicInteger();
        composition.project(runtime -> {
            SceneNode root = new SceneNode();
            root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
            runtime.on(root, SceneEventType.POINTER_DOWN, (event, context) -> { });
            runtime.on(root, SceneEventType.POINTER_CANCEL, (event, context) -> {
                cancels.incrementAndGet();
                composition.close();
            });
            return root;
        }, 0, 0);
        source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);
        composition.drainAndRoute();

        composition.close();
        composition.close();

        Assert.assertEquals(1, cancels.get());
    }

    @Test
    public void cancelHandlerReleasingCaptureDoesNotDuplicateCancelToThePressedNode() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        AtomicInteger cancels = new AtomicInteger();
        composition.project(runtime -> {
            SceneNode root = new SceneNode();
            root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
            runtime.on(root, SceneEventType.POINTER_DOWN,
                    (event, context) -> context.requestPointerCapture());
            runtime.on(root, SceneEventType.POINTER_CANCEL, (event, context) -> {
                cancels.incrementAndGet();
                runtime.getInputRouter().releasePointerCapture();
            });
            return root;
        }, 0, 0);
        source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);
        composition.drainAndRoute();

        composition.close();

        Assert.assertEquals(1, cancels.get());
    }

    @Test
    public void emptyFrameDoesNotResetTheActiveGestureCancelTimestamp() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        long[] cancelTime = {-1L};
        composition.project(runtime -> {
            SceneNode root = new SceneNode();
            root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
            runtime.on(root, SceneEventType.POINTER_DOWN, (event, context) -> { });
            runtime.on(root, SceneEventType.POINTER_CANCEL,
                    (event, context) -> cancelTime[0] = event.getTimeNanos());
            return root;
        }, 0, 0);
        source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);
        composition.drainAndRoute();
        composition.drainAndRoute();

        composition.close();

        Assert.assertEquals(1L, cancelTime[0]);
    }

    @Test
    public void closeInsideDownWaitsForRouterThenCancelsExactlyOnce() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        AtomicInteger cancels = new AtomicInteger();
        AtomicInteger cleanups = new AtomicInteger();
        composition.project(runtime -> {
            Owner.current().onCleanup(cleanups::incrementAndGet);
            SceneNode root = new SceneNode();
            root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
            runtime.on(root, SceneEventType.POINTER_DOWN, (event, context) -> composition.close());
            runtime.on(root, SceneEventType.POINTER_CANCEL, (event, context) -> cancels.incrementAndGet());
            return root;
        }, 0, 0);
        source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);

        composition.drainAndRoute();
        composition.close();

        Assert.assertEquals(1, cancels.get());
        Assert.assertEquals(1, cleanups.get());
    }

    @Test
    public void closeInsideDownCancelsAtTheCurrentEventInsteadOfTheFrameTail() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        int[] cancelLocal = {-1, -1};
        composition.project(runtime -> {
            SceneNode root = new SceneNode();
            root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
            runtime.on(root, SceneEventType.POINTER_DOWN, (event, context) -> composition.close());
            runtime.on(root, SceneEventType.POINTER_CANCEL, (event, context) -> {
                cancelLocal[0] = context.getLocalPointerX();
                cancelLocal[1] = context.getLocalPointerY();
            });
            return root;
        }, 50, 40);
        source.enqueuePointer(ScenePointerAction.BUTTON_DOWN, 55, 45, SceneMouseButton.LEFT);
        source.enqueuePointer(ScenePointerAction.MOVE, 90, 90, SceneMouseButton.NONE);

        composition.drainAndRoute();

        Assert.assertArrayEquals(new int[] {5, 5}, cancelLocal);
    }

    @Test
    public void closeInsideFirstKeyStopsLaterKeysFromTheSameNativeFrame() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        AtomicInteger delivered = new AtomicInteger();
        SceneProjectionComposition.Occurrence occurrence = composition.project(runtime -> {
            SceneNode root = new SceneNode();
            runtime.focusable(root);
            runtime.on(root, SceneEventType.KEY_DOWN, (event, context) -> {
                delivered.incrementAndGet();
                if (event.getKey() == SceneKey.ENTER) {
                    composition.close();
                }
            });
            return root;
        }, 0, 0);
        occurrence.runtime().requestFocus(occurrence.root());
        source.enqueueKey(SceneKey.ENTER, SceneKeyAction.PRESSED);
        source.enqueueKey(SceneKey.SPACE, SceneKeyAction.PRESSED);

        composition.drainAndRoute();

        Assert.assertEquals(1, delivered.get());
    }

    @Test
    public void projectionFactoryUsesAnOccurrenceOwnerInsteadOfTheAmbientOwner() {
        CountingInputSource source = new CountingInputSource(200, 200);
        SceneProjectionComposition composition = new SceneProjectionComposition(source);
        Owner ambient = new Owner();
        AtomicInteger occurrenceCleanups = new AtomicInteger();
        ambient.run(() -> composition.project(runtime -> {
            Assert.assertNotSame(ambient, Owner.current());
            Owner.current().onCleanup(occurrenceCleanups::incrementAndGet);
            return new SceneNode();
        }, 0, 0));

        composition.close();

        Assert.assertEquals(1, occurrenceCleanups.get());
        Assert.assertFalse(ambient.isDisposed());
        ambient.dispose();
    }

    private static final class CountingInputSource implements PlatformInputSource {
        private final MockPlatformInputSource delegate;
        private int drainCount;

        private CountingInputSource(int width, int height) {
            delegate = new MockPlatformInputSource(width, height);
        }

        private void enqueuePointer(ScenePointerAction action, int x, int y, SceneMouseButton button) {
            delegate.enqueuePointer(action, x, y, button, 0, 0, 0,
                    false, false, false, false, 1L);
        }

        private void enqueueKey(SceneKey key, SceneKeyAction action) {
            delegate.enqueueKey(key, action, false, false, false, false, 2L);
        }

        private int drainCount() {
            return drainCount;
        }

        @Override
        public SceneInputFrame drainFrame() {
            drainCount++;
            return delegate.drainFrame();
        }

        @Override
        public int logicalWidth() {
            return delegate.logicalWidth();
        }

        @Override
        public int logicalHeight() {
            return delegate.logicalHeight();
        }
    }
}
