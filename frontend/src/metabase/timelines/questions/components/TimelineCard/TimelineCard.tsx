import React, {
  ChangeEvent,
  MouseEvent,
  memo,
  useCallback,
  useState,
  useEffect,
} from "react";
import _ from "underscore";
import { getTimelineName } from "metabase/lib/timelines";
import Ellipsified from "metabase/core/components/Ellipsified";
import { Timeline, TimelineEvent } from "metabase-types/api";
import EventCard from "../EventCard";
import {
  CardHeader,
  CardContent,
  CardLabel,
  CardCheckbox,
  CardIcon,
  CardRoot,
} from "./TimelineCard.styled";

export interface TimelineCardProps {
  timeline: Timeline;
  isDefault?: boolean;
  isVisible?: boolean;
  hiddenEventIds?: number[];
  selectedEventIds?: number[];
  onEditEvent?: (event: TimelineEvent) => void;
  onMoveEvent?: (event: TimelineEvent) => void;
  onArchiveEvent?: (event: TimelineEvent) => void;
  onToggleEventSelected?: (event: TimelineEvent, isSelected: boolean) => void;
  onToggleEventVisibility?: (event: TimelineEvent, isSelected: boolean) => void;
  onToggleTimeline?: (timeline: Timeline, isVisible: boolean) => void;
}

const TimelineCard = ({
  timeline,
  isDefault,
  isVisible,
  hiddenEventIds = [],
  selectedEventIds = [],
  onEditEvent,
  onMoveEvent,
  onArchiveEvent,
  onToggleEventSelected,
  onToggleEventVisibility,
  onToggleTimeline,
}: TimelineCardProps): JSX.Element => {
  const events = getEvents(timeline.events);
  const isEventSelected = events.some(e => selectedEventIds.includes(e.id));
  const [isExpanded, setIsExpanded] = useState(isDefault || isEventSelected);

  const handleHeaderClick = useCallback(() => {
    setIsExpanded(isExpanded => !isExpanded);
  }, []);

  const handleCheckboxClick = useCallback((event: MouseEvent) => {
    event.stopPropagation();
  }, []);

  const handleToggleTimeline = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      onToggleTimeline?.(timeline, event.target.checked);
    },
    [timeline, onToggleTimeline],
  );

  const handleToggleEventSelected = useCallback(
    (event: TimelineEvent, isSelected: boolean) => {
      onToggleEventSelected?.(event, isSelected);

      if (isSelected && !isVisible) {
        onToggleTimeline?.(timeline, true);
      }
    },
    [timeline, isVisible, onToggleTimeline, onToggleEventSelected],
  );

  useEffect(() => {
    if (isEventSelected) {
      setIsExpanded(isEventSelected);
    }
  }, [isEventSelected, selectedEventIds]);

  return (
    <CardRoot>
      <CardHeader onClick={handleHeaderClick}>
        <CardCheckbox
          checked={isVisible}
          onClick={handleCheckboxClick}
          onChange={handleToggleTimeline}
        />
        <CardLabel>
          <Ellipsified tooltipMaxWidth="100%">
            {getTimelineName(timeline)}
          </Ellipsified>
        </CardLabel>
        <CardIcon name={isExpanded ? "chevronup" : "chevrondown"} />
      </CardHeader>
      {isExpanded && (
        <CardContent>
          {events.map(event => (
            <EventCard
              key={event.id}
              event={event}
              timeline={timeline}
              isSelected={selectedEventIds.includes(event.id)}
              isHidden={hiddenEventIds.includes(event.id)}
              onEdit={onEditEvent}
              onMove={onMoveEvent}
              onArchive={onArchiveEvent}
              onToggleSelected={handleToggleEventSelected}
              onToggleEventVisibility={onToggleEventVisibility}
            />
          ))}
        </CardContent>
      )}
    </CardRoot>
  );
};

const getEvents = (events: TimelineEvent[] = []) => {
  return _.chain(events)
    .sortBy(e => e.timestamp)
    .reverse()
    .value();
};

export default memo(TimelineCard);
