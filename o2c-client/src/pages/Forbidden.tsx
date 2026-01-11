import { Link } from 'react-router-dom';

export function Forbidden() {
  return (
    <div className="max-w-4xl mx-auto px-4 py-10">
      <div className="bg-white shadow-sm rounded-lg p-8 border border-gray-200">
        <h1 className="text-2xl font-semibold text-gray-900">403 Forbidden</h1>
        <p className="mt-2 text-gray-600">You don’t have permission to access this page.</p>
        <div className="mt-6">
          <Link
            to="/orders"
            className="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-md transition-colors"
          >
            Back to Orders
          </Link>
        </div>
      </div>
    </div>
  );
}
