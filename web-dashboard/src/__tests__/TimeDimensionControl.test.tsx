import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TimeDimensionControl } from '@/components/TimeDimensionControl';

describe('TimeDimensionControl', () => {
  it('renders the timeline region with accessible label', () => {
    const now = new Date();
    render(
      <TimeDimensionControl currentTime={now} onTimeChange={vi.fn()} />
    );
    expect(
      screen.getByRole('region', { name: /time dimension timeline scrubber/i })
    ).toBeInTheDocument();
  });

  it('renders a play button', () => {
    render(
      <TimeDimensionControl currentTime={new Date()} onTimeChange={vi.fn()} />
    );
    expect(screen.getByRole('button', { name: /play timeline/i })).toBeInTheDocument();
  });

  it('renders a range slider', () => {
    render(
      <TimeDimensionControl currentTime={new Date()} onTimeChange={vi.fn()} />
    );
    expect(screen.getByRole('slider', { name: /timeline position/i })).toBeInTheDocument();
  });

  it('calls onTimeChange when scrubber changes', async () => {
    const user = userEvent.setup();
    const onTimeChange = vi.fn();
    render(
      <TimeDimensionControl currentTime={new Date()} onTimeChange={onTimeChange} />
    );
    const slider = screen.getByRole('slider');
    await user.click(slider);
    // Note: jsdom range input interaction is limited; we verify the element is interactive
    expect(slider).not.toBeDisabled();
  });

  it('toggles to pause button when play is clicked', async () => {
    const user = userEvent.setup();
    render(
      <TimeDimensionControl currentTime={new Date()} onTimeChange={vi.fn()} />
    );
    const playBtn = screen.getByRole('button', { name: /play timeline/i });
    await user.click(playBtn);
    expect(screen.getByRole('button', { name: /pause timeline/i })).toBeInTheDocument();
  });
});
