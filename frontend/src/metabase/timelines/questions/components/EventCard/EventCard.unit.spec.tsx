import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  createMockTimeline,
  createMockTimelineEvent,
} from "metabase-types/api/mocks";
import EventCard, { EventCardProps } from "./EventCard";

describe("EventCard", () => {
  it("should render an event with date", () => {
    const props = getProps({
      event: createMockTimelineEvent({
        timestamp: "2020-01-01T10:20:00Z",
        time_matters: false,
      }),
    });

    render(<EventCard {...props} />);

    expect(screen.getByText("January 1, 2020"));
  });

  it("should render an event with date and time", () => {
    const props = getProps({
      event: createMockTimelineEvent({
        timestamp: "2020-01-01T10:20:00Z",
        time_matters: true,
      }),
    });

    render(<EventCard {...props} />);

    expect(screen.getByText("January 1, 2020, 10:20 AM"));
  });

  it("should toggle an event's visibility", () => {
    const props = getProps({
      event: createMockTimelineEvent({
        timestamp: "2020-01-01T10:20:00Z",
        time_matters: true,
      }),
    });

    render(<EventCard {...props} />);

    userEvent.click(screen.getByRole("checkbox"));

    expect(props.onToggleEventVisibility).toHaveBeenCalledWith(
      props.event,
      false,
    );
  });
});

const getProps = (opts?: Partial<EventCardProps>): EventCardProps => ({
  event: createMockTimelineEvent(),
  timeline: createMockTimeline(),
  isTimelineVisible: true,
  isVisible: true,
  onToggleEventVisibility: jest.fn(),
  ...opts,
});
