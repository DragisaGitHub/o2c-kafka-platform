import type { TimelineEvent } from '../types';
import { StatusBadge } from './StatusBadge';

interface TimelineProps {
  events: TimelineEvent[];
}

function formatTimestamp(value: string): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? date.toLocaleString() : '—';
}

export function Timeline({ events }: TimelineProps) {
  if (!events || events.length === 0) {
    return (
      <div className="text-center py-8 text-gray-500">
        No timeline events available
      </div>
    );
  }

  return (
    <div className="flow-root">
      <ul className="-mb-8">
        {events.map((event, eventIdx) => (
          <li key={`${event.service}-${event.timestamp || 'no-ts'}-${event.status}-${eventIdx}`}>
            <div className="relative pb-8">
              {eventIdx !== events.length - 1 && (
                <span
                  className="absolute top-5 left-5 -ml-px h-full w-0.5 bg-gray-200"
                  aria-hidden="true"
                />
              )}
              <div className="relative flex items-start space-x-3">
                <div className="relative">
                  <div
                    className={`h-10 w-10 rounded-full flex items-center justify-center ring-8 ring-white ${
                      event.service === 'order'
                        ? 'bg-blue-500'
                        : event.service === 'checkout'
                        ? 'bg-purple-500'
                        : 'bg-green-500'
                    }`}
                  >
                    <span className="text-white text-xs uppercase">
                      {event.service.charAt(0)}
                    </span>
                  </div>
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-2">
                      <span className="text-sm text-gray-900 capitalize">
                        {event.service}
                      </span>
                      <StatusBadge status={event.status} />
                    </div>
                    <time className="text-sm text-gray-500">
                      {formatTimestamp(event.timestamp)}
                    </time>
                  </div>
                  {event.message && (
                    <p className="mt-1 text-sm text-gray-600">
                      {event.message}
                    </p>
                  )}
                  {event.failureReason && (
                    <div className="mt-2 bg-red-50 border border-red-200 rounded p-2">
                      <p className="text-sm text-red-800">
                        <span className="mr-1">⚠️</span>
                        {event.failureReason}
                      </p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
