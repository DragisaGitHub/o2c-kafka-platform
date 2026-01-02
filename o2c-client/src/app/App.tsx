import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import {Navigation} from "../components/Navigation";
import {CreateOrder} from "../pages/CreateOrder";
import {Orders} from "../pages/Orders";
import {OrderDetails} from "../pages/OrderDetails";

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-gray-50">
        <Navigation />
        <Routes>
          <Route path="/" element={<Navigate to="/orders" replace />} />
          <Route path="/create" element={<CreateOrder />} />
          <Route path="/orders" element={<Orders />} />
          <Route path="/orders/:orderId" element={<OrderDetails />} />
          <Route path="*" element={<Navigate to="/orders" replace />} />
        </Routes>
      </div>
    </BrowserRouter>
  );
}
