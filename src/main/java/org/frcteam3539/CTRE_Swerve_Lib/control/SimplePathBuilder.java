package org.frcteam3539.CTRE_Swerve_Lib.control;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.frcteam3539.CTRE_Swerve_Lib.util.Angles;

public final class SimplePathBuilder {
    private List<PathSegment> segmentList = new ArrayList<>();
    private TreeMap<Double, Rotation2d> rotationMap = new TreeMap<>();

    private Translation2d lastPosition;
    private double length = 0.0;

    /**
     * A class used to build a path with simple lines and arcs.
     * @param initialPose the starting pose of the robot at the beginning of this path.
     */
    public SimplePathBuilder(Pose2d initialPose) {
        this.lastPosition = initialPose.getTranslation();

        rotationMap.put(0.0, initialPose.getRotation());
    }

    private void addSegment(PathSegment segment) {
        segmentList.add(segment);
        length += segment.getLength();
        lastPosition = segment.getEnd().getPosition();
    }

    private void addSegment(PathSegment segment, Rotation2d rotation) {
        addSegment(segment);
        rotationMap.put(length, rotation);
    }

    /**
     * Build the path.
     * @return The path.
     */
    public Path build() {
        return new Path(segmentList.toArray(new PathSegment[0]), rotationMap);
    }

    /**
     * Add an arc to the path without a specific robot rotation. It will keep the last robot rotation.
     * @param position 
     * @return SimplePathBuilder object to add more segments to or to use to build the path.
     */
    public SimplePathBuilder arcTo(Translation2d position, Translation2d center, boolean clockwise) {
        addSegment(new ArcSegment(lastPosition, position, center, clockwise));
        return this;
    }

    /**
     * Add an arc to the path with a specific robot rotation.
     * @param pose The pose of the robot at the end of that arc segment. 
     * @return SimplePathBuilder object to add more segments to or to use to build the path.
     */
    public SimplePathBuilder arcTo(Pose2d pose, Translation2d center, boolean clockwise) {
        addSegment(new ArcSegment(lastPosition, pose.getTranslation(), center, clockwise), pose.getRotation());
        return this;
    }

    /**
     * Add an arc to the path without a specific robot rotation. It will keep the last robot rotation.
     * @param position 
     * @return SimplePathBuilder object to add more segments to or to use to build the path.
     */
    public SimplePathBuilder arcTo(Translation2d position, Translation2d center) {
        addSegment(new ArcSegment(lastPosition, position, center));
        return this;
    }

    /**
     * Add an arc to the path with a specific robot rotation.
     * @param pose The pose of the robot at the end of that arc segment. 
     * @return SimplePathBuilder object to add more segments to or to use to build the path.
     */
    public SimplePathBuilder arcTo(Pose2d pose, Translation2d center) {
        addSegment(new ArcSegment(lastPosition, pose.getTranslation(), center), pose.getRotation());
        return this;
    }

    /**
     * Add a line to the path without a specific robot rotation. It will keep the last robot rotation.
     * @param position 
     * @return SimplePathBuilder object to add more segments to or to use to build the path.
     */
    public SimplePathBuilder lineTo(Translation2d position) {
        addSegment(new LineSegment(lastPosition, position));
        return this;
    }
    /**
     * Add a line to the path with a specific robot rotation.
     * @param pose The pose of the robot at the end of that line segment. 
     * @return SimplePathBuilder object to add more segments to or to use to build the path.
     */
    public SimplePathBuilder lineTo(Pose2d pose) {
        addSegment(new LineSegment(lastPosition, pose.getTranslation()), pose.getRotation());
        return this;
    }

    private static final class ArcSegment extends PathSegment {
        private final Translation2d center;
        private final Translation2d deltaStart;
        private final Translation2d deltaEnd;
        private final boolean clockwise;
        private final Rotation2d arcAngle;

        private final double curvature;

        private  final double length;

        public ArcSegment(Translation2d start, Translation2d end, Translation2d center) {
            this.center = center;
            deltaStart = start.minus(center);
            deltaEnd = end.minus(center);

            var cross = deltaStart.getX() * deltaEnd.getY() - deltaStart.getY() * deltaEnd.getX();
            clockwise = cross <= 0.0;

            var r1 = new Rotation2d(deltaStart.getX(), deltaStart.getY());
            var r2 = new Rotation2d(deltaEnd.getX(), deltaEnd.getY());

            arcAngle = Rotation2d.fromDegrees(
                    Math.toDegrees(Angles.shortestAngularDistance(r1.getRadians(), r2.getRadians())));

            curvature = 1.0 / deltaStart.getNorm();
            length = deltaStart.getNorm() * arcAngle.getRadians();
        }

        public ArcSegment(Translation2d start, Translation2d end, Translation2d center, boolean clockwise)
        {
            this.center = center;
            deltaStart = start.minus(center);
            deltaEnd = end.minus(center);

            var cross = deltaStart.getX() * deltaEnd.getY() - deltaStart.getY() * deltaEnd.getX();
            boolean isClockwise = cross <= 0.0;

            var r1 = new Rotation2d(deltaStart.getX(), deltaStart.getY());
            var r2 = new Rotation2d(deltaEnd.getX(), deltaEnd.getY());

            this.clockwise = clockwise;
            if(isClockwise != clockwise)
            {
                arcAngle = Rotation2d.fromDegrees(
                    Math.toDegrees(Angles.shortestAngularDistance(r1.getRadians(), r2.getRadians()))).minus(Rotation2d.fromDegrees(360));
            }
            else
            {
                arcAngle = Rotation2d.fromDegrees(
                    Math.toDegrees(Angles.shortestAngularDistance(r1.getRadians(), r2.getRadians())));
            }

            curvature = 1.0 / deltaStart.getNorm();
            length = deltaStart.getNorm() * arcAngle.getRadians();
        }

        @Override
        public State calculate(double distance) {
            double percentage = distance / length;

            Translation2d sampleHeading = deltaStart.rotateBy(Rotation2d.fromDegrees(percentage + (clockwise ? -1.0 : 1.0) * 90));
            Rotation2d newHeading = new Rotation2d(sampleHeading.getX(), sampleHeading.getY());

            return new State(
                    center.plus(deltaStart.rotateBy(Rotation2d.fromDegrees(percentage))),
                    newHeading,
                    curvature
            );
        }

        @Override
        public double getLength() {
            return length; //deltaStart.length * Vector2.getAngleBetween(deltaStart, deltaEnd).toRadians();
        }
    }

    private static final class LineSegment extends PathSegment {
        private final Translation2d start;
        private final Translation2d delta;
        private final Rotation2d heading;

        private LineSegment(Translation2d start, Translation2d end) {
            this.start = start;
            this.delta = end.minus(start);
            this.heading = new Rotation2d(delta.getX(), delta.getY());
        }

        @Override
        public State calculate(double distance) {
            return new State(
                    start.plus(delta.times(distance / getLength())),
                    heading,
                    0.0
            );
        }

        @Override
        public double getLength() {
            return delta.getNorm();
        }
    }
}
