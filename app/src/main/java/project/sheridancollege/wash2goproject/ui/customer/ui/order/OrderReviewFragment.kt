package project.sheridancollege.wash2goproject.ui.customer.ui.order

import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.android.volley.AuthFailureError
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.PaymentSheetResultCallback
import org.json.JSONException
import org.json.JSONObject
import project.sheridancollege.wash2goproject.R
import project.sheridancollege.wash2goproject.common.Order
import project.sheridancollege.wash2goproject.common.User
import project.sheridancollege.wash2goproject.databinding.FragmentOrderReviewBinding
import project.sheridancollege.wash2goproject.firebase.FCMHandler
import project.sheridancollege.wash2goproject.ui.customer.CustomerActivity
import project.sheridancollege.wash2goproject.util.SharedPreferenceUtils
import java.util.HashMap

class OrderReviewFragment : Fragment() {

    private lateinit var binding: FragmentOrderReviewBinding
    private lateinit var order: Order
    private var user: User? = null
    private lateinit var orderReviewViewModel: OrderReviewViewModel
    private lateinit var progressDialog: ProgressDialog

    val SECRET_KEY: String = "sk_test_51IYCM4H0mP6JyTKcR60Pw6rjcvFVZwQFnE5ff0SI5nPeHIhTrJA3Pk4Vm33vghCTJGJOINq3kajdAjmDFkdnCUie00Q4sBQina";
    val PUBLISH_KEY: String = "pk_test_51IYCM4H0mP6JyTKc3o5iVqi61CdwUxlFBq2uLqinZYWKPCnBHx3aiEHLdDz1JG1tlKWHWdOckSgM2HUZLGRkl8LL00VjMSyP80";
    var emphericalKey: String = ""
    var clientSecret: String? = null
    private val CUST_ID: String = "cus_MgAGrVJee9hqOo"
    var paymentSheet: PaymentSheet? = null;

    companion object {
        val TAG: String = OrderReviewFragment::class.java.simpleName
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)

        PaymentConfiguration.init(requireActivity(), PUBLISH_KEY )

        paymentSheet = PaymentSheet(
            this,
            PaymentSheetResultCallback { paymentSheetResult: PaymentSheetResult? ->
                onPaymentResult(paymentSheetResult);
            })

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)




        arguments?.let {
            order = it.get("order") as Order
        }
        Log.e(TAG, "Order : " + Gson().toJson(order))
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {



        orderReviewViewModel =
            ViewModelProvider(this).get(OrderReviewViewModel::class.java)

        // Inflate the layout for this fragment
        binding =
            DataBindingUtil.inflate(
                inflater,
                R.layout.fragment_order_review,
                container,
                false
            )

        progressDialog = ProgressDialog(requireContext())
        progressDialog.setMessage("Please wait, fetching payment details...")
        progressDialog.setCancelable(false)


        user = SharedPreferenceUtils.getUserDetails()
        binding.customerName.text = user?.firstName + " " + user?.lastName
        binding.orderNumber.text = order.orderId
        binding.orderDate.text = order.orderDateTime.split(" ")[0]
        binding.orderTime.text = order.orderDateTime.split(" ")[1]
        binding.carType.text = order.carType
        binding.servicePayment.text = "$${order.servicePrice}"
        binding.addOnsPayment.text = "$${order.addOnsPrice}"
        binding.totalPayment.text = "$${order.totalPrice}"


        binding.detailerName.text = "Not Available"
        binding.detailerNumber.text = "Not Available"

        orderReviewViewModel.getDetailerData(order.detailerId)
        orderReviewViewModel.detailer.observe(viewLifecycleOwner) {
            binding.detailerName.text = it?.firstName + " " + it?.lastName
            binding.detailerNumber.text = it?.phone
        }

        binding.cancelBtn.setOnClickListener(View.OnClickListener {
            findNavController().navigate(R.id.action_go_back_to_home)
        })

        binding.paymentBtn.setOnClickListener(View.OnClickListener {
            progressDialog.show()

            var stringRequest: StringRequest = object : StringRequest(
                Method.POST,
                "https://api.stripe.com/v1/ephemeral_keys",
                Response.Listener { response ->
                    try {
                        val obj : JSONObject = JSONObject(response)
                        emphericalKey = obj.getString("id")
                        getClientSecret(CUST_ID, emphericalKey)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                },
                Response.ErrorListener { }) {
                @Throws(AuthFailureError::class)
                override fun getHeaders(): Map<String, String> {
                    val header: MutableMap<String, String> = HashMap()
                    header["Authorization"] = "Bearer $SECRET_KEY"
                    header["Stripe-Version"] = "2020-08-27"
                    return header
                }

                @Throws(AuthFailureError::class)
                override fun getParams(): Map<String, String>? {
                    val body: MutableMap<String, String> = HashMap()
                    body["customer"] = CUST_ID!!
                    return body
                }
            }

            var requesQueue : RequestQueue = Volley.newRequestQueue(requireActivity());
            requesQueue.add( stringRequest);


        })

        orderReviewViewModel.orderResult.observe(viewLifecycleOwner) {
            if (progressDialog.isShowing) progressDialog.dismiss()
            Toast.makeText(requireContext(), "Order saved successfully!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_go_back_to_home)
        }

        return binding.root
    }

    fun getClientSecret(cust_id: String?, emphKey: String?) {
        val stringRequest: StringRequest = object : StringRequest(
            Method.POST,
            "https://api.stripe.com/v1/payment_intents",
            Response.Listener { response ->
                try {
                    val `object` = JSONObject(response)
                    clientSecret = `object`.getString("client_secret")
                    progressDialog.cancel();
                    paymentFlow()
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            },
            Response.ErrorListener { }) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                val header: MutableMap<String, String> = HashMap()
                header["Authorization"] = "Bearer $SECRET_KEY"
                return header
            }

            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String>? {
                val body: MutableMap<String, String> = HashMap()
                body["customer"] = CUST_ID!!
                body["amount"] = "${order.totalPrice}" + "00"
                body["currency"] = "cad"
                body["automatic_payment_methods[enabled]"] = "true"
                return body
            }
        }

        var requesQueue : RequestQueue = Volley.newRequestQueue(requireActivity());
        requesQueue.add( stringRequest);
    }

    private fun paymentFlow() {
        paymentSheet!!.presentWithPaymentIntent(
            clientSecret!!,
            PaymentSheet.Configuration(
                "Wash2Go",
                PaymentSheet.CustomerConfiguration(CUST_ID, emphericalKey)
            )
        )
    }

    private fun onPaymentResult(paymentSheetResult: PaymentSheetResult?) {
        if (paymentSheetResult is PaymentSheetResult.Completed) {
            orderReviewViewModel.insertOrder(order)
            Toast.makeText(requireActivity(), "Done", Toast.LENGTH_SHORT)
        }
    }
}
